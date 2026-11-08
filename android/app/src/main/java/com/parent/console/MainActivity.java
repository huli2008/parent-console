package com.parent.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Parent console: glass UI, three themes, one card per device. */
public class MainActivity extends Activity {

    private static final String PREFS = "parent_console";
    private static final String KEY_THEME = "theme";

    private final List<Device> devices = new ArrayList<>();
    private Theme theme;
    private Backdrop backdrop;
    private FrameLayout root;
    private LinearLayout list;
    private TextView countView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        theme = Theme.of(getPreferences(MODE_PRIVATE).getInt(KEY_THEME, Theme.AURORA));

        root = new FrameLayout(this);
        backdrop = new Backdrop(this, theme);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        page.setPadding(pad, dp(22), pad, dp(18));

        page.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(14), 0, dp(96));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        TextView scan = actionButton("扫描", false);
        scan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        scan.setOnClickListener(v -> scanLan());
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(dp(92), dp(52));
        scanLp.rightMargin = dp(10);
        bottom.addView(scan, scanLp);

        TextView add = actionButton("＋  添加设备", true);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        add.setOnClickListener(v -> showEdit(null, null));
        bottom.addView(add, new LinearLayout.LayoutParams(0, dp(52), 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(pad, 0, pad, dp(22));
        lp.gravity = Gravity.BOTTOM;
        root.addView(bottom, lp);

        setContentView(root);

        devices.addAll(Store.load(this));
        render();
        if (!devices.isEmpty()) refreshAll();
    }

    // ---------- header ----------

    private View buildHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("家长端");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        titleRow.addView(title);

        TextView count = new TextView(this);
        countView = count;
        count.setText("  " + devices.size() + " 台设备");
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        count.setTextColor(theme.dim);
        titleRow.addView(count);

        box.addView(titleRow);

        TextView sub = new TextView(this);
        sub.setText("同一 Wi-Fi · 每台电视填 IP 即可");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        sub.setTextColor(theme.dim);
        sub.setPadding(0, dp(4), 0, 0);
        box.addView(sub);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(14), 0, 0);
        for (Theme t : Theme.ALL) {
            chips.addView(themeChip(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chips.getChildAt(chips.getChildCount() - 1).setLayoutParams(lp);
        }
        hs.addView(chips);
        box.addView(hs);
        return box;
    }

    private View themeChip(final Theme t) {
        TextView chip = new TextView(this);
        boolean on = t.id == theme.id;
        chip.setText(t.name);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setTextColor(on ? theme.accentText : theme.text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(on ? t.accent : theme.card);
        bg.setStroke(dp(1), on ? t.accent : theme.cardStroke);
        chip.setBackground(bg);
        chip.setOnClickListener(v -> {
            theme = t;
            getPreferences(MODE_PRIVATE).edit().putInt(KEY_THEME, t.id).apply();
            recreate();
        });
        return chip;
    }

    // ---------- device cards ----------

    private void render() {
        list.removeAllViews();
        if (countView != null) countView.setText("  " + devices.size() + " 台设备");
        if (devices.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("还没有设备\n\n点左下角「扫描」自动找同一 Wi-Fi 上的电视，\n或点「添加设备」手动填 IP，再填家长密码。");
            e.setTextColor(theme.dim);
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(10), dp(70), dp(10), dp(10));
            list.addView(e, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        list.addView(batchRow());
        for (Device d : devices) list.addView(cardFor(d));
    }

    /** Bulk actions for every configured device. */
    private View batchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(10));
        row.addView(chipButton("全部刷新", this::refreshAll));
        row.addView(chipButton("全部锁定", () -> batch("lock", 0)));
        row.addView(chipButton("全部解锁 30 分", () -> batch("unlock", 30)));
        return row;
    }

    private void batch(String action, int minutes) {
        int n = 0;
        for (Device d : devices) {
            boolean hasAuth = (d.pass != null && !d.pass.isEmpty()) || (d.token != null && !d.token.isEmpty());
            if (!hasAuth) continue;
            n++;
            Api.remote(this, d, action, minutes, new Api.Cb() {
                @Override
                public void ok(String text) {
                }

                @Override
                public void fail(String message, int status) {
                }
            });
        }
        if (n == 0) {
            toast("这些设备还没填家长密码");
            return;
        }
        toast("已向 " + n + " 台设备发送");
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::refreshAll, 1200);
    }

    private View cardFor(final Device d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(theme.card);
        bg.setStroke(dp(1), theme.cardStroke);
        card.setBackground(bg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);

        // title row: dot + name + status word
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(dotColor(d));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(9);
        row.addView(dot, dotLp);

        TextView name = new TextView(this);
        name.setText(d.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextColor(theme.text);
        row.addView(name);

        TextView addr = new TextView(this);
        addr.setText("  " + d.host);
        addr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        addr.setTextColor(theme.dim);
        row.addView(addr);

        card.addView(row);

        TextView status = new TextView(this);
        status.setText(d.dot == 0 && d.lastSeen > 0 ? (d.status == null ? "未连接" : d.status) + "\n上次在线 " + new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(d.lastSeen)) : (d.status == null ? "未连接" : d.status));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setTextColor(theme.dim);
        status.setPadding(0, dp(7), 0, dp(2));
        card.addView(status);

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setPadding(0, dp(9), 0, 0);

        acts.addView(chipButton("刷新", () -> refresh(d)));
        acts.addView(chipButton("解锁 30 分", () -> remote(d, "unlock", 30)));
        acts.addView(chipButton("锁定", () -> remote(d, "lock", 0)));
        acts.addView(chipButton("面板", () -> openPanel(d)));
        card.addView(acts);

        card.setOnClickListener(v -> openPanel(d));
        card.setOnLongClickListener(v -> {
            showActions(d);
            return true;
        });
        return card;
    }

    private TextView chipButton(String label, final Runnable r) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        b.setTextColor(theme.text);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(theme.light ? Color.parseColor("#66FFFFFF") : Color.parseColor("#22FFFFFF"));
        bg.setStroke(dp(1), theme.cardStroke);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(7);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private int dotColor(Device d) {
        if (d.dot == 1) return Color.parseColor("#38D39F");
        if (d.dot == 2) return Color.parseColor("#F5A524");
        if (d.dot == 0) return Color.parseColor("#F2555A");
        return theme.dim;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? theme.accentText : theme.text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(primary ? theme.accent : theme.card);
        if (!primary) bg.setStroke(dp(1), theme.cardStroke);
        b.setBackground(bg);
        return b;
    }

    // ---------- data actions ----------

    private void refreshAll() {
        for (Device d : devices) refresh(d);
    }

    private void refresh(final Device d) {
        d.status = "连接中…";
        d.dot = 3;
        render();
        Api.state(this, d, new Api.Cb() {
            @Override
            public void ok(String text) {
                d.status = text;
                d.dot = 1;
                d.lastSeen = System.currentTimeMillis();
                Store.save(MainActivity.this, devices);
                render();
            }

            @Override
            public void fail(String message, int status) {
                d.dot = status == 401 ? 2 : 0;
                d.status = status == 401 ? "密码不对，或电视上改过密码" : ("连不上：" + message);
                render();
            }
        });
    }

    private void remote(final Device d, String action, int minutes) {
        toast("正在发送…");
        Api.remote(this, d, action, minutes, new Api.Cb() {
            @Override
            public void ok(String text) {
                toast(text);
                refresh(d);
            }

            @Override
            public void fail(String message, int status) {
                toast(message);
            }
        });
    }

    private void openPanel(Device d) {
        startActivity(new Intent(this, WebActivity.class)
                .putExtra("host", d.host)
                .putExtra("pass", d.pass)
                .putExtra("token", d.token)
                .putExtra("theme", theme.id));
    }

    private void showActions(final Device d) {
        String[] items = {"刷新状态", "解锁 30 分钟", "立即锁定", "打开管理面板", "编辑", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(d.name)
                .setItems(items, (dlg, which) -> {
                    switch (which) {
                        case 0: refresh(d); break;
                        case 1: remote(d, "unlock", 30); break;
                        case 2: remote(d, "lock", 0); break;
                        case 3: openPanel(d); break;
                        case 4: showEdit(d); break;
                        default: confirmDelete(d); break;
                    }
                })
                .show();
    }

    private void confirmDelete(final Device d) {
        new AlertDialog.Builder(this)
                .setMessage("删除「" + d.name + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dlg, w) -> {
                    devices.remove(d);
                    Store.save(this, devices);
                    render();
                })
                .show();
    }

    /** Scans the Wi-Fi for StudyOnTV devices and offers the ones not added yet. */
    private void scanLan() {
        final TextView progress = new TextView(this);
        progress.setText("正在扫描 192.168.x.x …");
        progress.setTextColor(theme.dim);
        progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int p = dp(20);
        progress.setPadding(p, dp(16), p, dp(16));

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("扫描局域网")
                .setView(progress)
                .setNegativeButton("取消", null)
                .create();
        dlg.show();

        Discovery.scan(this, new Discovery.Cb() {
            @Override
            public void onProgress(int scanned, int total, int found) {
                progress.setText("已扫描 " + scanned + "/" + total
                        + (found > 0 ? "  ·  发现 " + found + " 台" : " …"));
            }

            @Override
            public void onFound(String host) {
                progress.setText("发现 " + host + " …");
            }

            @Override
            public void onFinished(java.util.List<String> hosts) {
                if (!dlg.isShowing()) return;
                java.util.List<String> fresh = new java.util.ArrayList<>();
                for (String h : hosts) {
                    boolean known = false;
                    for (Device d : devices) if (h.equals(d.host)) known = true;
                    if (!known) fresh.add(h);
                }
                dlg.dismiss();
                if (fresh.isEmpty()) {
                    toast(hosts.isEmpty() ? "没有发现设备：确认电视开机、与本机同一 Wi-Fi"
                            : "发现的设备都已添加");
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("发现 " + fresh.size() + " 台设备")
                        .setItems(fresh.toArray(new String[0]),
                                (d2, which) -> showEdit(null, fresh.get(which)))
                        .setNegativeButton("关闭", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                if (dlg.isShowing()) dlg.dismiss();
                toast(message);
            }
        });
    }

    private void showEdit(final Device existing) {
        showEdit(existing, null);
    }

    private void showEdit(final Device existing, final String presetHost) {
        final boolean isNew = existing == null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(6), pad, 0);

        final EditText name = input(box, "名称", isNew ? "" : existing.name, false, "客厅电视");

        LinearLayout addrRow = new LinearLayout(this);
        addrRow.setOrientation(LinearLayout.HORIZONTAL);
        addrRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(addrRow);

        TextView label = new TextView(this);
        label.setText("电视 IP");
        label.setTextColor(theme.dim);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(0, dp(10), 0, dp(4));
        addrRow.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText host = new EditText(this);
        host.setHint("192.168.8.192");
        host.setText(existing != null ? existing.host : (presetHost == null ? "" : presetHost));
        host.setSingleLine(true);
        host.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        host.setTextColor(theme.text);
        host.setHintTextColor(theme.dim);
        host.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable hb = new GradientDrawable();
        hb.setCornerRadius(dp(12));
        hb.setColor(theme.light ? Color.parseColor("#99FFFFFF") : Color.parseColor("#33FFFFFF"));
        hb.setStroke(dp(1), theme.cardStroke);
        host.setBackground(hb);
        host.setPadding(dp(12), dp(9), dp(12), dp(9));
        box.addView(host);

        final EditText pass = input(box, "家长密码（可选）", isNew ? "" : existing.pass, true, "电视上设置的密码");

        new AlertDialog.Builder(this)
                .setTitle(isNew ? "添加设备" : "编辑设备")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dlg, w) -> {
                    String h = normalizeHost(host.getText().toString());
                    if (h.isEmpty()) {
                        toast("请填电视 IP");
                        return;
                    }
                    String n = name.getText().toString().trim();
                    if (isNew) {
                        Device d = new Device();
                        d.id = String.valueOf(System.currentTimeMillis());
                        d.name = n.isEmpty() ? h : n;
                        d.host = h;
                        d.pass = pass.getText().toString();
                        devices.add(d);
                        Store.save(this, devices);
                        render();
                        // 填完 IP 和密码直接进控制页面（面板登录由 WebActivity 注入完成）
                        openPanel(d);
                        refresh(d);
                    } else {
                        existing.name = n.isEmpty() ? h : n;
                        existing.host = h;
                        existing.pass = pass.getText().toString();
                        existing.token = null;
                        Store.save(this, devices);
                        render();
                        refresh(existing);
                    }
                })
                .show();
    }

    /** Keeps only the host part: strips scheme, port, path. */
    private static String normalizeHost(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int colon = h.indexOf(':');
        if (colon > 0) h = h.substring(0, colon);
        return h;
    }

    private EditText input(LinearLayout parent, String label, String value,
                           boolean password, String hint) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(theme.dim);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setPadding(0, dp(10), 0, dp(4));
        parent.addView(l);

        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        e.setTextColor(theme.text);
        e.setHintTextColor(theme.dim);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(theme.light ? Color.parseColor("#99FFFFFF") : Color.parseColor("#33FFFFFF"));
        bg.setStroke(dp(1), theme.cardStroke);
        e.setBackground(bg);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        parent.addView(e);
        return e;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void recreateCompat() {
        recreate();
    }
}
