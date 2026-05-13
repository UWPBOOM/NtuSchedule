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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

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

    // ⭐ 学年 / 学期选项
    data class YearOption(val value: String, val label: String)
    data class SemesterOption(val value: String, val label: String)

    val yearOptions = remember {
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        ((now - 5)..(now + 5)).map { y -> YearOption(y.toString(), "$y-${y + 1}") }
    }
    val semesterOptions = listOf(
        SemesterOption("3", "第一学期"),
        SemesterOption("12", "第二学期")
    )

    // ⭐ 纯系统时间推断，不记用户选择：8月后=第一学期(学年=今年)，1月=第一学期(学年=去年)，2-7月=第二学期(学年=去年)
    val (smartYear, smartSemester) = remember {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val year = cal.get(java.util.Calendar.YEAR)
        when {
            month >= 8 -> year.toString() to "3"
            month == 1 -> (year - 1).toString() to "3"
            else -> (year - 1).toString() to "12"
        }
    }

    var selectedYear by remember { mutableStateOf(smartYear) }
    var selectedSemester by remember { mutableStateOf(smartSemester) }
    var showYearDialog by remember { mutableStateOf(false) }
    var showSemesterDialog by remember { mutableStateOf(false) }

    // JS 字符串安全转义，防止引号/反斜杠/换行破坏语法
    fun String.escapeForJs(): String {
        val sb = StringBuilder(this.length + 8)
        for (ch in this) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

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

                    Spacer(modifier = Modifier.height(16.dp))

                    // ⭐ 学年选择（OutlinedTextField 风格，透明遮罩捕获点击）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = yearOptions.find { it.value == selectedYear }?.label ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("学年") },
                            trailingIcon = { Text("▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showYearDialog = true })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ⭐ 学期选择（OutlinedTextField 风格，透明遮罩捕获点击）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = semesterOptions.find { it.value == selectedSemester }?.label ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("学期") },
                            trailingIcon = { Text("▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showSemesterDialog = true })
                    }

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
                                        val safeUrl = url

                                        if (safeUrl.contains("xskbcx_cxXskbcxIndex")) {
                                            progressText = "正在切换学年学期并查询课表..."
                                            val js = """
                                                javascript:(function() {
                                                    var xnm = document.getElementById('xnm');
                                                    var xqm = document.getElementById('xqm');
                                                    var btn = document.getElementById('search_go');
                                                    if (xnm) {
                                                        xnm.value = '${selectedYear.escapeForJs()}';
                                                        xnm.dispatchEvent(new Event('change', { bubbles: true }));
                                                    }
                                                    if (xqm) {
                                                        xqm.value = '${selectedSemester.escapeForJs()}';
                                                        xqm.dispatchEvent(new Event('change', { bubbles: true }));
                                                    }
                                                    setTimeout(function() {
                                                        if (btn) btn.click();
                                                        setTimeout(function() {
                                                            window.HTMLOUT.processHTML(document.documentElement.outerHTML);
                                                        }, 3000);
                                                    }, 600);
                                                })();
                                            """.trimIndent()
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

                                            // 5秒超时监控 — 使用 WeakReference 避免生命周期问题
                                            val weakRef = java.lang.ref.WeakReference(webViewRef)
                                            scope.launch {
                                                delay(5000)
                                                if (!isCrawling) return@launch
                                                val wv = weakRef.get()
                                                val currentUrl = wv?.url
                                                if (currentUrl?.contains("login") == true || currentUrl?.endsWith("jwglxt/") == true) {
                                                    isManualMode = true
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

    // ⭐ 学年弹窗
    if (showYearDialog) {
        AlertDialog(
            onDismissRequest = { showYearDialog = false },
            title = { Text("选择学年", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    yearOptions.forEach { option ->
                        val isSelected = option.value == selectedYear
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedYear = option.value
                                showYearDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    option.label,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("✓", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showYearDialog = false }) { Text("取消") } }
        )
    }

    // ⭐ 学期弹窗
    if (showSemesterDialog) {
        AlertDialog(
            onDismissRequest = { showSemesterDialog = false },
            title = { Text("选择学期", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    semesterOptions.forEach { option ->
                        val isSelected = option.value == selectedSemester
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedSemester = option.value
                                showSemesterDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    option.label,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("✓", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSemesterDialog = false }) { Text("取消") } }
        )
    }
}