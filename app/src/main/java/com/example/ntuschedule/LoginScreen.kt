package com.example.ntuschedule

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onBack: () -> Unit, onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedId by PreferencesManager.getUserId(context).collectAsState(initial = "")
    val savedPwd by PreferencesManager.getPassword(context).collectAsState(initial = "")

    var stuId by remember(savedId) { mutableStateOf(savedId) }
    var password by remember(savedPwd) { mutableStateOf(savedPwd) }

    var isCrawling by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("准备就绪") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 手动接管模式标记
    var isManualMode by remember { mutableStateOf(false) }

    // JS 字符串安全转义，防止引号/反斜杠破坏语法
    fun String.escapeForJs(): String =
        this.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    BackHandler(enabled = isCrawling) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            isCrawling = false
            isManualMode = false // 退出时重置
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCrawling) "教务系统直连" else "智能导入") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isCrawling) {
                            isCrawling = false
                            isManualMode = false
                        } else onBack()
                    }) {
                        Icon(if (isCrawling) Icons.Default.Close else Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            if (!isCrawling) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("教务系统自动登录", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("遇卡顿会自动暂停，支持手动接管", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(48.dp))

                    OutlinedTextField(
                        value = stuId,
                        onValueChange = { stuId = it },
                        label = { Text("学号") },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            scope.launch { PreferencesManager.saveCredentials(context, stuId, password) }
                            isCrawling = true
                            isManualMode = false
                            progressText = "正在加载教务系统..."
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = stuId.isNotBlank() && password.isNotBlank()
                    ) {
                        Text("一键智能导入", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部状态栏：根据是否进入手动模式改变颜色和文字
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isManualMode) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isManualMode) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = progressText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isManualMode) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true

                                webChromeClient = object : WebChromeClient() {
                                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                        post { Toast.makeText(context, "网页提示: $message", Toast.LENGTH_LONG).show() }
                                        result?.confirm()
                                        return true
                                    }
                                }

                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun processHTML(html: String) {
                                        post {
                                            isCrawling = false
                                            isManualMode = false
                                            onLoginSuccess(html)
                                        }
                                    }
                                }, "HTMLOUT")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        val safeUrl = url ?: ""

                                        if (safeUrl.contains("xskbcx_cxXskbcxIndex")) {
                                            progressText = "已到达课表页，正在抓取数据..."
                                            val js = "javascript:setTimeout(function() { window.HTMLOUT.processHTML(document.documentElement.outerHTML); }, 1500);"
                                            view.evaluateJavascript(js, null)
                                        }
                                        else if (safeUrl.contains("index") || safeUrl.contains("main") || safeUrl.contains("initMenu")) {
                                            progressText = "登录成功！正在跳转到课表页..."
                                            view.loadUrl("https://tdjw.ntu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508&layout=default")
                                        }
                                        else if (safeUrl.contains("login") || safeUrl.endsWith("jwglxt/")) {
                                            // 如果已经进入手动模式，就不再注入JS，完全交给你操作
                                            if (isManualMode) return

                                            progressText = "尝试自动登录中 (如卡住请手动操作)..."

                                            // ⭐ 注入完美版的 JS 脚本
                                            val js = """
                                                javascript:(function() {
                                                    // 延迟0.5秒执行，等待网页底层框架加载完毕
                                                    setTimeout(function() {
                                                        var u = document.getElementById('yhm') || document.querySelector('input[type="text"]');
                                                        var p = document.getElementById('mm') || document.querySelector('input[type="password"]');

                                                        if(u && p) {
                                                            u.value = '${stuId.escapeForJs()}';
                                                            p.value = '${password.escapeForJs()}';
                                                            
                                                            // 触发事件，让教务系统的 Vue/jQuery 知道我们填了密码
                                                            u.dispatchEvent(new Event('input', { bubbles: true }));
                                                            p.dispatchEvent(new Event('input', { bubbles: true }));
                                                            u.dispatchEvent(new Event('change', { bubbles: true }));
                                                            p.dispatchEvent(new Event('change', { bubbles: true }));
                                                            
                                                            // 等待1秒让登录按钮的状态激活，然后点击
                                                            setTimeout(function() {
                                                                try {
                                                                    // 🎯 直接通过 ID 精准定位移动端按钮
                                                                    var btn = document.getElementById('login_submit') 
                                                                           || document.querySelector('.btn_login')
                                                                           || document.getElementById('dl');
                                                                    
                                                                    if(btn) {
                                                                        btn.click();
                                                                    } else {
                                                                        console.log("未找到登录按钮");
                                                                    }
                                                                } catch(e) { console.log(e); }
                                                            }, 1000);
                                                        }
                                                    }, 500);
                                                })();
                                            """.trimIndent()
                                            view.evaluateJavascript(js, null)

                                            // 5秒超时监控
                                            scope.launch {
                                                delay(5000) // 等待 5 秒
                                                // 如果5秒后已退出抓取状态，跳过
                                                if (!isCrawling) return@launch
                                                // 如果当前页面还是登录页，说明卡住了
                                                val currentUrl = webViewRef?.url
                                                if (currentUrl?.contains("login") == true || currentUrl?.endsWith("jwglxt/") == true) {
                                                    isManualMode = true // 开启手动模式
                                                    progressText = "自动点击失败，请您手动点击【登录】按钮！"
                                                }
                                            }
                                        }
                                    }
                                }
                                loadUrl("https://tdjw.ntu.edu.cn/jwglxt/")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}