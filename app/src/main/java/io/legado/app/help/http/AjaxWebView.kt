package io.legado.app.help.http

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.webkit.*
import java.lang.ref.WeakReference
import java.util.*


class AjaxWebView {
    var callback: Callback? = null
    private var mHandler: AjaxHandler

    init {
        mHandler = AjaxHandler(this)
    }

    class AjaxHandler(private val ajaxWebView: AjaxWebView) : Handler(Looper.getMainLooper()) {

        private var mWebView: WebView? = null

        override fun handleMessage(msg: Message) {
            val params: AjaxParams
            when (msg.what) {
                MSG_AJAX_START -> {
                    params = msg.obj as AjaxParams
                    mWebView = createAjaxWebView(params, this)
                }
                MSG_SNIFF_START -> {
                    params = msg.obj as AjaxParams
                    mWebView = createAjaxWebView(params, this)
                }
                MSG_SUCCESS -> {
                    ajaxWebView.callback?.onResult(msg.obj as String)
                    destroyWebView()
                }
                MSG_ERROR -> {
                    ajaxWebView.callback?.onError(msg.obj as Throwable)
                    destroyWebView()
                }
            }
        }

        @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
        fun createAjaxWebView(params: AjaxParams, handler: Handler): WebView {
            val webView = WebView(params.context.applicationContext)
            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = true
            settings.userAgentString = params.userAgent
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (params.isSniff) {
                webView.webViewClient = SnifferWebClient(params, handler)
            } else {
                webView.webViewClient = HtmlWebViewClient(params, handler)
                webView.addJavascriptInterface(JavaInjectMethod(handler), "OUTHTML")
            }
            when (params.requestMethod) {
                RequestMethod.POST -> webView.postUrl(params.url, params.postData)
                RequestMethod.GET, RequestMethod.DEFAULT -> webView.loadUrl(
                    params.url,
                    params.headerMap
                )
            }
            return webView
        }

        private fun destroyWebView() {
            mWebView?.destroy()
            mWebView = null
        }


    }

    fun ajax(params: AjaxParams) {
        mHandler.obtainMessage(MSG_AJAX_START, params)
            .sendToTarget()
    }

    fun sniff(params: AjaxParams) {
        mHandler.obtainMessage(MSG_SNIFF_START, params)
            .sendToTarget()
    }

    fun destroyWebView() {
        mHandler.obtainMessage(DESTROY_WEB_VIEW)
    }

    class JavaInjectMethod(private val handler: Handler) {

        @JavascriptInterface
        fun processHTML(html: String) {
            handler.obtainMessage(MSG_SUCCESS, html)
                .sendToTarget()
        }
    }


    class AjaxParams(val context: Context, private val tag: String) {
        var requestMethod: RequestMethod? = null
            get() {
                return field ?: RequestMethod.DEFAULT
            }
        var url: String? = null
        var postData: ByteArray? = null
        var headerMap: Map<String, String>? = null
        var cookieStore: CookieStore? = null
        var audioSuffix: String? = null
        var javaScript: String? = null
        private var audioSuffixList: List<String>? = null

        val userAgent: String?
            get() = if (this.headerMap != null) {
                this.headerMap!!.get("User-Agent")
            } else null

        val isSniff: Boolean
            get() = !TextUtils.isEmpty(audioSuffix)

        fun requestMethod(method: RequestMethod): AjaxParams {
            this.requestMethod = method
            return this
        }

        fun url(url: String): AjaxParams {
            this.url = url
            return this
        }

        fun postData(postData: ByteArray): AjaxParams {
            this.postData = postData
            return this
        }

        fun headerMap(headerMap: Map<String, String>): AjaxParams {
            this.headerMap = headerMap
            return this
        }

        fun cookieStore(cookieStore: CookieStore): AjaxParams {
            this.cookieStore = cookieStore
            return this
        }

        fun suffix(suffix: String): AjaxParams {
            this.audioSuffix = suffix
            return this
        }

        fun javaScript(javaScript: String): AjaxParams {
            this.javaScript = javaScript
            return this
        }

        fun setCookie(url: String) {
            if (cookieStore != null) {
                val cookie = CookieManager.getInstance().getCookie(url)
                cookieStore?.setCookie(tag, cookie)
            }
        }

        fun hasJavaScript(): Boolean {
            return !TextUtils.isEmpty(javaScript)
        }

        fun clearJavaScript() {
            javaScript = null
        }

        fun getAudioSuffixList(): List<String>? {
            if (audioSuffixList == null) {
                audioSuffixList = if (isSniff) {
                    audioSuffix?.split("\\|\\|".toRegex())
                } else {
                    Collections.emptyList()
                }
            }
            return audioSuffixList
        }
    }

    class HtmlWebViewClient(
        private val params: AjaxParams,
        private val handler: Handler
    ) : WebViewClient() {


        override fun onPageFinished(view: WebView, url: String) {
            params.setCookie(url)
            evaluateJavascript(view)
        }


        override fun onLoadResource(view: WebView, url: String) {
            super.onLoadResource(view, url)
        }

        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                handler.obtainMessage(MSG_ERROR, Exception(description))
                    .sendToTarget()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                handler.obtainMessage(
                    MSG_ERROR,
                    Exception(error.description.toString())
                )
                    .sendToTarget()
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.proceed()
        }

        private fun evaluateJavascript(webView: WebView) {
            val runnable = ScriptRunnable(webView, OUTER_HTML)
            handler.postDelayed(runnable, 1000L)
        }

        companion object {

            const val OUTER_HTML =
                "window.OUTHTML.processHTML('<head>'+document.getElementsByTagName('html')[0].innerHTML+'</head>');"
        }
    }

    class SnifferWebClient(
        private val params: AjaxParams,
        private val handler: Handler
    ) : WebViewClient() {

        override fun onLoadResource(view: WebView, url: String) {
            val suffixList = params.getAudioSuffixList()
            for (suffix in suffixList!!) {
                if (!TextUtils.isEmpty(suffix) && url.contains(suffix)) {
                    handler.obtainMessage(MSG_SUCCESS, url)
                        .sendToTarget()
                    break
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                handler.obtainMessage(MSG_ERROR, Exception(description))
                    .sendToTarget()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                handler.obtainMessage(
                    MSG_ERROR,
                    Exception(error.description.toString())
                )
                    .sendToTarget()
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.proceed()
        }

        override fun onPageFinished(view: WebView, url: String) {
            params.setCookie(url)
            if (params.hasJavaScript()) {
                evaluateJavascript(view, params.javaScript)
                params.clearJavaScript()
            }
        }

        private fun evaluateJavascript(webView: WebView, javaScript: String?) {
            val runnable = ScriptRunnable(webView, javaScript)
            handler.postDelayed(runnable, 1000L)
        }
    }

    class ScriptRunnable(
        webView: WebView,
        private val mJavaScript: String?
    ) : Runnable {

        private val mWebView: WeakReference<WebView> = WeakReference(webView)

        override fun run() {
            mWebView.get()?.loadUrl("javascript:${mJavaScript ?: ""}")
        }
    }

    companion object {
        const val MSG_AJAX_START = 0
        const val MSG_SNIFF_START = 1
        const val MSG_SUCCESS = 2
        const val MSG_ERROR = 3
        const val DESTROY_WEB_VIEW = 4
    }

    enum class RequestMethod {
        GET, POST, DEFAULT
    }

    interface CookieStore {
        fun setCookie(url: String, cookie: String)

        fun replaceCookie(url: String, cookie: String)

        fun getCookie(url: String): String

        fun removeCookie(url: String)

        fun clearCookies()
    }

    abstract class Callback {

        abstract fun onResult(result: String)

        abstract fun onError(error: Throwable)
    }
}