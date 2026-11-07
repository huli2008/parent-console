package com.parent.console;

/** One managed device (a TV or tablet running StudyOnTV). */
public class Device {
    public String id;
    public String name;
    public String host;   // "192.168.8.192" or "192.168.8.192:8099"
    public String pass;   // admin passphrase, optional
    public String token;  // cached bearer token
    public String status; // last status text
    public int dot;       // 0 offline, 1 online, 2 unknown/needs password

    public String base() {
        String h = host == null ? "" : host.trim();
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        int slash = h.indexOf('/');
        if (slash > 0) h = h.substring(0, slash);
        return h.matches(".*:\\d+$") ? h : h + ":8099";
    }
}
