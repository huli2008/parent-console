package com.parent.console;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds StudyOnTV devices on the current Wi-Fi.
 *
 * <p>Scans the /24 around our own IPv4 address for TCP :8099, then confirms the host by asking
 * {@code /api/state}: a StudyOnTV device answers 401 with {@code {"error":"unauthorized"}}.
 */
public final class Discovery {

    public interface Cb {
        void onProgress(int scanned, int total, int found);

        void onFound(String host);

        void onFinished(List<String> hosts);

        void onError(String message);
    }

    private static final int PORT = 8099;
    private static final int CONNECT_TIMEOUT_MS = 350;
    private static final int READ_TIMEOUT_MS = 700;
    private static final int THREADS = 64;

    private Discovery() {
    }

    public static void scan(final Context ctx, final Cb cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String cidr = localSubnet(ctx);
                if (cidr == null) {
                    main.post(() -> cb.onError("没有连上 Wi-Fi，或拿不到本机地址"));
                    return;
                }
                String prefix = cidr.substring(0, cidr.lastIndexOf('.') + 1);
                String self = localIp(ctx);
                final int total = 254;
                final AtomicInteger done = new AtomicInteger();
                final List<String> found = Collections.synchronizedList(new ArrayList<String>());
                final CountDownLatch latch = new CountDownLatch(total);
                ExecutorService pool = Executors.newFixedThreadPool(THREADS);
                for (int i = 1; i <= total; i++) {
                    final String ip = prefix + i;
                    pool.execute(() -> {
                        try {
                            if (!ip.equals(self) && probe(ip)) {
                                found.add(ip);
                                final int f = found.size();
                                main.post(() -> {
                                    cb.onFound(ip);
                                    cb.onProgress(done.get(), total, f);
                                });
                            }
                        } finally {
                            int n = done.incrementAndGet();
                            if (n % 16 == 0 || n == total) {
                                main.post(() -> cb.onProgress(n, total, found.size()));
                            }
                            latch.countDown();
                        }
                    });
                }
                latch.await(30, TimeUnit.SECONDS);
                pool.shutdownNow();
                Collections.sort(found);
                final List<String> result = new ArrayList<>(found);
                main.post(() -> cb.onFinished(result));
            } catch (Exception e) {
                main.post(() -> cb.onError(e.getMessage() == null ? "扫描失败" : e.getMessage()));
            }
        }, "lan-scan").start();
    }

    /** TCP reachable and answers like a StudyOnTV panel. */
    private static boolean probe(String ip) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, PORT), CONNECT_TIMEOUT_MS);
        } catch (Exception e) {
            return false;
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + ip + ":" + PORT + "/api/state").openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code != 401 && code != 200) {
                return false;
            }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            return body != null && (body.contains("unauthorized") || body.contains("learning"));
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) {
        if (in == null) return null;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[512];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String localIp(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp != null) {
                for (LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** "192.168.8." prefix of the /24 to scan (never wider than /24). */
    private static String localSubnet(Context ctx) {
        String ip = localIp(ctx);
        if (ip == null) return null;
        int dot = ip.lastIndexOf('.');
        return dot > 0 ? ip.substring(0, dot + 1) : null;
    }
}
