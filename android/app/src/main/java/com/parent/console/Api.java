package com.parent.console;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal client for the controller API served by StudyOnTV on :8099. */
public final class Api {

    public interface Cb {
        void ok(String text);
        void fail(String message, int status);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Api() {}

    public static void state(final Context ctx, final Device d, final Cb cb) {
        POOL.execute(() -> {
            try {
                String token = ensureToken(d);
                JSONObject json = call(d, "/api/state", "GET", null, token);
                final String text = describe(json);
                MAIN.post(() -> cb.ok(text));
            } catch (HttpError e) {
                MAIN.post(() -> cb.fail(e.getMessage(), e.status));
            } catch (Exception e) {
                MAIN.post(() -> cb.fail(readable(e), 0));
            }
        });
    }

    public static void remote(final Context ctx, final Device d, final String action,
                              final int minutes, final Cb cb) {
        POOL.execute(() -> {
            try {
                String token = ensureToken(d);
                JSONObject body = new JSONObject();
                body.put("action", action);
                body.put("minutes", minutes);
                JSONObject json = call(d, "/api/remote", "POST", body, token);
                final String text = "unlock".equals(action) ? "已解锁"
                        : "lock".equals(action) ? "已锁定" : "已执行";
                if (json.has("learning")) describe(json); // keep token fresh, ignore text
                MAIN.post(() -> cb.ok(text));
            } catch (HttpError e) {
                MAIN.post(() -> cb.fail(e.getMessage(), e.status));
            } catch (Exception e) {
                MAIN.post(() -> cb.fail(readable(e), 0));
            }
        });
    }

    /** Returns a bearer token for the device, logging in with the stored passphrase if needed. */
    public static String token(Device d) throws Exception {
        return ensureToken(d);
    }

    private static String ensureToken(Device d) throws Exception {
        if (d.token != null && !d.token.isEmpty()) return d.token;
        if (d.pass == null || d.pass.isEmpty()) {
            throw new HttpError("需要填写管理密码", 401);
        }
        JSONObject body = new JSONObject();
        body.put("passphrase", d.pass);
        body.put("remember", true);
        JSONObject res = call(d, "/api/auth", "POST", body, null);
        String token = res.optString("token", null);
        if (token == null) token = res.optString("adminToken", null);
        if (token == null) token = res.optString("key", null);
        if (token == null || token.isEmpty()) throw new HttpError("登录失败", 401);
        d.token = token;
        return token;
    }

    private static JSONObject call(Device d, String path, String method,
                                   JSONObject body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + d.base() + path).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestMethod(method);
        c.setRequestProperty("Content-Type", "application/json");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            byte[] bytes = body.toString().getBytes("UTF-8");
            c.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = c.getOutputStream();
            os.write(bytes);
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = read(in);
        JSONObject json;
        try {
            json = new JSONObject(text == null || text.isEmpty() ? "{}" : text);
        } catch (Exception e) {
            throw new HttpError("设备返回异常，请重试", code);
        }
        if (code >= 400 || json.has("error")) {
            String msg = json.optString("message", null);
            if (msg == null) msg = json.optString("error", "请求失败");
            throw new HttpError(msg, code);
        }
        return json;
    }

    private static String describe(JSONObject s) {
        JSONObject l = s.optJSONObject("learning");
        StringBuilder sb = new StringBuilder();
        if (l != null) {
            String mode = l.optString("mode", "");
            if ("unlock".equals(mode)) sb.append("已解锁");
            else if ("lock".equals(mode)) sb.append("已锁定");
            if (l.optBoolean("free", false)) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("自由使用");
            }
            int left = l.optInt("remainingSeconds", -1);
            if (left >= 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("剩余 ").append(fmt(left));
            }
        }
        if (sb.length() == 0) sb.append("在线");
        JSONObject dev = s.optJSONObject("device");
        if (dev != null) {
            String name = dev.optString("name", "");
            if (!name.isEmpty()) sb.append("\n").append(name);
        }
        return sb.toString();
    }

    private static String fmt(int sec) {
        int h = sec / 3600, m = (sec % 3600) / 60;
        return h > 0 ? (h + " 小时 " + m + " 分") : (m + " 分钟");
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private static String readable(Exception e) {
        String m = e.getMessage();
        if (m == null) return "网络错误";
        if (m.contains("timeout") || m.contains("timed out")) return "连接超时，确认同一 Wi-Fi";
        return m;
    }

    public static class HttpError extends Exception {
        public final int status;
        HttpError(String msg, int status) {
            super(msg);
            this.status = status;
        }
    }
}
