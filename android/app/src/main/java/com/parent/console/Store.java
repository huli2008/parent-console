package com.parent.console;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;

/** Device list persisted as JSON in SharedPreferences. */
public final class Store {
    private static final String PREFS = "parent_console";
    private static final String KEY = "devices";

    private Store() {}

    public static List<Device> load(Context ctx) {
        List<Device> out = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Device d = new Device();
                d.id = o.optString("id");
                d.name = o.optString("name");
                d.host = o.optString("host");
                d.pass = o.optString("pass");
                d.token = o.optString("token", null);
                d.lastSeen = o.optLong("lastSeen", 0L);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context ctx, List<Device> devices) {
        JSONArray arr = new JSONArray();
        for (Device d : devices) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", d.id);
                o.put("name", d.name);
                o.put("host", d.host);
                o.put("pass", d.pass);
                o.put("token", d.token == null ? JSONObject.NULL : d.token);
                o.put("lastSeen", d.lastSeen);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
