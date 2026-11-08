package com.parent.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;

/**
 * 设备控制页：内嵌设备自带的家长面板，并提供返回 / 主页 / 刷新 / 菜单工具栏。
 * 打开前用已保存的密码换 token，并注入面板自己的会话（localStorage "sotv-panel-session"）。
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
    private final Handler ui = new Handler(Looper.getMainLooper());

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
                String t = null;
                try {
                    t = Api.token(d);
                } catch (Exception ignored) {
                }
                final String ft = t;
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (ft != null) token = ft;
                    buildAndLoad();
                });
            }).start();
            return;
        }
        buildAndLoad();
    }

    private String panelUrl() {
        return "http://" + host + ":8099/admin/";
    }

    private void buildAndLoad() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.bgBottom);

        page.addView(buildToolbar());

        final ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(theme.accent);
        progress.setProgressDrawable(pb);
        progress.setVisibility(View.GONE);
        page.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

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
                if (url == null || !url.contains(":8099")) return;
                if (token != null && !token.isEmpty() && !recovered) {
                    recovered = true;
                    String js = "!!localStorage.getItem(" + JSONObject.quote(SESSION_KEY) + ")";
                    view.evaluateJavascript(js, value -> {
                        if (isFinishing()) return;
                        if (!"true".equals(value)) {
                            view.evaluateJavascript(bootstrapJs(), null);
                            view.loadUrl(panelUrl());
                            return;
                        }
                        view.evaluateJavascript(prefillJs(), null);
                    });
                } else {
                    view.evaluateJavascript(prefillJs(), null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        page.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        web.loadUrl(panelUrl());
    }

    /** 顶部工具栏：返回 · 主页 · 刷新 · 菜单 */
    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.card);
        bar.setBackground(bg);

        TextView back = barButton("‹ 返回");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView home = barButton("主页");
        home.setOnClickListener(v -> {
            if (web != null) web.loadUrl(panelUrl());
        });
        bar.addView(home);

        TextView reload = barButton("刷新");
        reload.setOnClickListener(v -> {
            if (web != null) web.reload();
        });
        bar.addView(reload);

        LinearLayout spacer = new LinearLayout(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView title = new TextView(this);
        title.setText(host);
        title.setTextColor(theme.dim);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setSingleLine(true);
        bar.addView(title);

        LinearLayout spacer2 = new LinearLayout(this);
        bar.addView(spacer2, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView more = barButton("⋯");
        more.setOnClickListener(v -> showMenu());
        bar.addView(more);
        return bar;
    }

    private TextView barButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(theme.text);
        b.setPadding(dp(10), dp(7), dp(10), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(theme.light ? Color.parseColor("#66FFFFFF") : Color.parseColor("#22FFFFFF"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void showMenu() {
        String[] items = {"在浏览器打开", "复制地址", "回到设备列表"};
        new AlertDialog.Builder(this)
                .setItems(items, (dlg, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(panelUrl())));
                    } else if (which == 1) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", panelUrl()));
                        Toast.makeText(this, "已复制 " + panelUrl(), Toast.LENGTH_SHORT).show();
                    } else {
                        finish();
                    }
                })
                .show();
    }

    private String bootstrapJs() {
        return "localStorage.setItem(" + JSONObject.quote(SESSION_KEY) + ","
                + "JSON.stringify({host:location.host,token:" + JSONObject.quote(token) + "}));";
    }

    /** 面板仍停在登录页时，把密码填进输入框（地址已是当前页）。 */
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

    /** 系统返回键 / 手势：直接回到设备列表（页面内导航用工具栏的返回）。 */
    @Override
    public void onBackPressed() {
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
