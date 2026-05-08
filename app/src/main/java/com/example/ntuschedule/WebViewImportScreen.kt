package com.example.ntuschedule // ⭐ 改为你的包名

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewImportScreen(onBack: () -> Unit, onImportHtml: (String) -> Unit) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 监听返回键，如果网页能退则退网页
    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录教务系统") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, "关闭导入") }
                }
            )
        },
        bottomBar = {
            // ⭐ 真正的网页控制台（带前进、后退、刷新）
            BottomAppBar(
                actions = {
                    IconButton(onClick = { webViewRef?.goBack() }) {
                        Icon(Icons.Default.ArrowBack, "后退网页")
                    }
                    IconButton(onClick = { webViewRef?.goForward() }) {
                        Icon(Icons.Default.ArrowForward, "前进网页")
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, "刷新页面")
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            // 最强 JS，连 iframe 画中画里的课表一起抓取！
                            val js = """
                                (function() {
                                    var html = document.documentElement.outerHTML;
                                    var frames = document.getElementsByTagName('iframe');
                                    for(var i=0; i<frames.length; i++) {
                                        try { html += frames[i].contentDocument.body.outerHTML; } catch(e) {}
                                    }
                                    return html;
                                })();
                            """.trimIndent()
                            webViewRef?.evaluateJavascript(js) { html ->
                                val unescapedHtml = html.replace("\\u003C", "<").removeSurrounding("\"")
                                onImportHtml(unescapedHtml)
                            }
                        },
                        icon = { Icon(Icons.Default.Check, "一键抓取") },
                        text = { Text("看到课表点我") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl("https://tdjw.ntu.edu.cn/jwglxt") // 你们的网址
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}