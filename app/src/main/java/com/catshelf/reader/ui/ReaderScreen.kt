package com.catshelf.reader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private const val ReaderToolbarHideThresholdPx = 96
private const val ReaderTopRevealThresholdPx = 20
private const val AgreementCookieMaxAgeSeconds = 15_552_000
private const val AgreementCookieName = "user-agreement"
private const val LazyImageRefreshDelayMs = 90L

@Composable
internal fun ReaderScreen(
    title: String,
    url: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isLoading by remember(url) { mutableStateOf(true) }
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }
    var controlsVisible by rememberSaveable(url) { mutableStateOf(true) }

    val webView = remember(url) {
        val view = WebView(context)
        val cookieManager = CookieManager.getInstance()
        var agreementRepairAttempted = false

        val tapDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    controlsVisible = !controlsVisible
                    return true
                }
            },
        )
        val lazyImageRefreshRunnable = Runnable {
            requestNiceCatLazyImageRefresh(view)
        }

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
        }

        cookieManager.apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }

        view.setOnTouchListener { _, event ->
            tapDetector.onTouchEvent(event)
            false
        }
        view.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            when {
                scrollY <= ReaderTopRevealThresholdPx -> controlsVisible = true
                scrollY > oldScrollY && scrollY >= ReaderToolbarHideThresholdPx -> controlsVisible = false
            }
            view.removeCallbacks(lazyImageRefreshRunnable)
            view.postDelayed(lazyImageRefreshRunnable, LazyImageRefreshDelayMs)
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                isLoading = true
                errorMessage = null
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                isLoading = false
                if ((view?.scrollY ?: 0) <= ReaderTopRevealThresholdPx) {
                    controlsVisible = true
                }

                val currentView = view ?: return
                installNiceCatLazyImageAssist(currentView)
                currentView.evaluateJavascript(agreementCookieCheckScript()) { result ->
                    val accepted = result == "true"
                    if (!accepted && !agreementRepairAttempted) {
                        agreementRepairAttempted = true
                        installNiceCatAgreementCookies(cookieManager) {
                            currentView.reload()
                        }
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    isLoading = false
                    errorMessage = error?.description?.toString().orEmpty().ifBlank { "阅读页加载失败" }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return false
                val host = target.host.orEmpty()
                val allowedHost = host == "nicecat.cc" || host.endsWith(".nicecat.cc")
                if (target.scheme == "https" && allowedHost) return false
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, target))
                }
                return true
            }
        }

        installNiceCatAgreementCookies(cookieManager) {
            view.loadUrl(url)
        }
        view
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) controlsVisible = true
    }

    DisposableEffect(activity, controlsVisible) {
        val window = activity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        if (window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, controlsVisible)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null && controller != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.setOnTouchListener(null)
            webView.setOnScrollChangeListener(null)
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xF217151C)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "←",
                        modifier = Modifier
                            .clickable(onClick = onClose)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "刷新",
                        modifier = Modifier
                            .clickable {
                                errorMessage = null
                                controlsVisible = true
                                installNiceCatAgreementCookies(CookieManager.getInstance()) {
                                    webView.reload()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        if (isLoading && !controlsVisible) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        errorMessage?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("阅读页加载失败", color = Color.White, fontSize = 20.sp)
                    Text(
                        message,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(16.dp),
                    )
                    Text(
                        "点击重试",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                errorMessage = null
                                controlsVisible = true
                                installNiceCatAgreementCookies(CookieManager.getInstance()) {
                                    webView.reload()
                                }
                            }
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun installNiceCatAgreementCookies(
    cookieManager: CookieManager,
    onComplete: () -> Unit,
) {
    val cookie = buildString {
        append(AgreementCookieName)
        append("=1; Path=/; Max-Age=")
        append(AgreementCookieMaxAgeSeconds)
        append("; Secure; SameSite=Lax")
    }
    val origins = listOf(
        "https://web.nicecat.cc",
        "https://nicecat.cc",
    )

    fun write(index: Int) {
        if (index >= origins.size) {
            cookieManager.flush()
            onComplete()
            return
        }
        cookieManager.setCookie(origins[index], cookie) {
            write(index + 1)
        }
    }
    write(0)
}

private fun agreementCookieCheckScript(): String = """
    (function () {
        try {
            var accepted = document.cookie.split(';').some(function (entry) {
                var pair = entry.trim().split('=');
                return pair[0] === '$AgreementCookieName' && pair.slice(1).join('=') === '1';
            });
            if (!accepted) {
                document.cookie = '$AgreementCookieName=1; path=/; max-age=$AgreementCookieMaxAgeSeconds; Secure; SameSite=Lax';
                return false;
            }
            return true;
        } catch (error) {
            return false;
        }
    })();
""".trimIndent()

private fun installNiceCatLazyImageAssist(webView: WebView) {
    webView.evaluateJavascript(lazyImageAssistScript(), null)
    webView.postDelayed(
        { requestNiceCatLazyImageRefresh(webView) },
        LazyImageRefreshDelayMs,
    )
    webView.postDelayed(
        { requestNiceCatLazyImageRefresh(webView) },
        320L,
    )
    webView.postDelayed(
        { requestNiceCatLazyImageRefresh(webView) },
        900L,
    )
}

private fun requestNiceCatLazyImageRefresh(webView: WebView) {
    webView.evaluateJavascript(
        "window.__haoMaoKickLazyImages && window.__haoMaoKickLazyImages();",
        null,
    )
}

private fun lazyImageAssistScript(): String = """
    (function () {
        if (window.__haoMaoLazyAssistInstalled) {
            if (window.__haoMaoKickLazyImages) {
                window.__haoMaoKickLazyImages();
            }
            return true;
        }

        window.__haoMaoLazyAssistInstalled = true;
        var dispatching = false;
        var primaryTimer = null;
        var followUpTimer = null;
        var finalTimer = null;

        function collectScrollTargets() {
            var targets = [
                window,
                document,
                document.scrollingElement,
                document.documentElement,
                document.body
            ];

            var candidates = document.querySelectorAll(
                '#__nuxt, #app, main, section, [id*="scroll"], [class*="scroll"]'
            );
            for (var i = 0; i < candidates.length; i += 1) {
                var element = candidates[i];
                if (element.scrollHeight > element.clientHeight + 16) {
                    targets.push(element);
                }
            }

            var blocks = document.querySelectorAll('div');
            for (var j = 0; j < blocks.length; j += 1) {
                var block = blocks[j];
                if (block.scrollHeight > block.clientHeight + 32) {
                    targets.push(block);
                }
            }

            return targets.filter(function (target, index, all) {
                return target && all.indexOf(target) === index;
            });
        }

        function dispatchLazyRefresh() {
            if (dispatching) return;
            dispatching = true;

            var targets = collectScrollTargets();
            for (var i = 0; i < targets.length; i += 1) {
                try {
                    targets[i].dispatchEvent(new Event('scroll', { bubbles: false }));
                } catch (ignored) {}
            }
            try {
                window.dispatchEvent(new Event('resize'));
            } catch (ignored) {}

            setTimeout(function () {
                dispatching = false;
            }, 0);
        }

        function scheduleLazyRefresh() {
            clearTimeout(primaryTimer);
            clearTimeout(followUpTimer);
            clearTimeout(finalTimer);
            primaryTimer = setTimeout(dispatchLazyRefresh, 70);
            followUpTimer = setTimeout(dispatchLazyRefresh, 260);
            finalTimer = setTimeout(dispatchLazyRefresh, 620);
        }

        window.__haoMaoKickLazyImages = scheduleLazyRefresh;

        document.addEventListener('scroll', function () {
            if (!dispatching) scheduleLazyRefresh();
        }, true);
        document.addEventListener('touchend', scheduleLazyRefresh, true);
        document.addEventListener('visibilitychange', function () {
            if (!document.hidden) scheduleLazyRefresh();
        });

        var observer = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i += 1) {
                if (mutations[i].addedNodes && mutations[i].addedNodes.length > 0) {
                    scheduleLazyRefresh();
                    break;
                }
            }
        });
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true
        });

        scheduleLazyRefresh();
        return true;
    })();
""".trimIndent()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
