package com.parent.console;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;

/**
 * Opens the device's panel at http://<ip>:8099/admin/ and injects the session the panel expects
 * (localStorage "sotv-panel-session" = {host, token}) so the user lands on the控制页面 directly
 * instead of the panel's own login screen.
 */
public class WebActivity extends Activity {

    private static final String SESSION_KEY = "sotv-panel-session";

    private WebView web;
    private Theme theme;
    private String host;
    private String token;
    private String pass;
    private volatile boolean bootstrapped = false;
    private volatile boolean recovered = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent it = getIntent();
        host = normalize(it.getStringExtra("host"));
        pass = it.getStringExtra("pass");
        token = it.getStringExtra("token");
        theme = Theme.of(it.getIntExtra("theme", Theme.AURORA));

        if ((token == null || token.isEmpty()) && pass != null && !pass.isEmpty()) {
            final Device d = new Device();
            d.host = host;
            d.pass = pass;
            new Thread(() -> {
                try {
                    String t = Api.token(d);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        token = t;
                        buildAndLoad();
                    });
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(this::buildAndLoad);
                }
            }).start();
            return;
        }
        buildAndLoad();
    }

    private void buildAndLoad() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setBackgroundColor(theme.bgBottom);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String url = req.getUrl() == null ? "" : req.getUrl().toString();
                if (!bootstrapped && token != null && !token.isEmpty() && isPanelEntry(url)) {
                    bootstrapped = true;
                    try {
                        String html = "<!doctype html><meta charset=\"utf-8\"><script>"
                                + bootstrapJs() + "location.replace('/admin/');</script>";
                        WebResourceResponse r = new WebResourceResponse(
                                "text/html", "utf-8", new ByteArrayInputStream(html.getBytes("UTF-8")));
                        java.util.Map<String, String> h = new java.util.HashMap<>();
                        h.put("Cache-Control", "no-store");
                        r.setResponseHeaders(h);
                        return r;
                    } catch (Exception ignored) {
                        return null;
                    }
                }
                return super.shouldInterceptRequest(view, req);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 面板若仍停在登录页：补一次会话，必要时把密码也填好（端口/路径都是固定的，无需手填）。
                if (token != null && !token.isEmpty() && !recovered) {
                    recovered = true;
                    String js = "!!localStorage.getItem(" + JSONObject.quote(SESSION_KEY) + ")";
                    view.evaluateJavascript(js, value -> {
                        if (!"true".equals(value)) {
                            view.evaluateJavascript(bootstrapJs(), null);
                            view.loadUrl("http://" + host + ":8099/admin/");
                            return;
                        }
                        view.evaluateJavascript(prefillJs(), null);
                    });
                } else {
                    view.evaluateJavascript(prefillJs(), null);
                }
            }
        });

        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(theme.accent);
        bar.setProgressDrawable(pb);
        bar.setVisibility(View.GONE);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(theme.bgBottom);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        barLp.gravity = android.view.Gravity.TOP;
        root.addView(bar, barLp);
        setContentView(root);

        web.loadUrl("http://" + host + ":8099/admin/");
    }

    private String bootstrapJs() {
        return "localStorage.setItem(" + JSONObject.quote(SESSION_KEY) + ","
                + "JSON.stringify({host:location.host,token:" + JSONObject.quote(token) + "}));";
    }

    /** Fills the panel's password field when it still shows its login screen. */
    private String prefillJs() {
        if (pass == null || pass.isEmpty()) return "(function(){})()";
        return "(function(){try{var i=document.querySelector('input[type=password]');if(!i)return;"
                + "var s=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;"
                + "s.call(i," + JSONObject.quote(pass) + ");"
                + "i.dispatchEvent(new Event('input',{bubbles:true}));}catch(e){}})()";
    }

    private boolean isPanelEntry(String url) {
        String base = "http://" + host + ":8099";
        if (!url.startsWith(base)) return false;
        String path = url.substring(base.length());
        return path.isEmpty() || "/".equals(path) || "/admin".equals(path) || "/admin/".equals(path);
    }

    private static String normalize(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int colon = h.indexOf(':');
        if (colon > 0) h = h.substring(0, colon);
        return h;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    public void onBackPressed() {
        // 直接回到设备列表，而不是在网页历史里一层层退。
        finish();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
