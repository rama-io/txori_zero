package com.rama.txori_zero.helpers;

/** HHMMSS helpers. Durations are stored as a six digit string, e.g. "000130". */
public final class Hms {
    private Hms() {
    }

    private static String pad2(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    public static String digits(String raw) {
        StringBuilder sb = new StringBuilder();
        if (raw != null) {
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c >= '0' && c <= '9') sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Keeps the last six digits, left padded with zeros. */
    public static String normalize(String raw) {
        String d = digits(raw);
        if (d.length() > 6) d = d.substring(d.length() - 6);
        while (d.length() < 6) d = "0" + d;
        return d;
    }

    public static long toSeconds(String raw) {
        String d = normalize(raw);
        long h = Long.parseLong(d.substring(0, 2));
        long m = Long.parseLong(d.substring(2, 4));
        long s = Long.parseLong(d.substring(4, 6));
        return h * 3600 + m * 60 + s;
    }

    public static long toMs(String raw) {
        return toSeconds(raw) * 1000L;
    }

    /** "000130" to "00:01:30". */
    public static String display(String raw) {
        String d = normalize(raw);
        return d.substring(0, 2) + ":" + d.substring(2, 4) + ":" + d.substring(4, 6);
    }

    public static String clock(long totalSeconds) {
        long t = Math.max(0, totalSeconds);
        return pad2(t / 3600) + ":" + pad2((t % 3600) / 60) + ":" + pad2(t % 60);
    }

    public static String clockMs(long ms) {
        return clock(ms / 1000);
    }

    /** Stopwatch style: 5.3 / 1:05.3 / 1:01:05.3 */
    public static String stopwatch(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        long tenths = (ms % 1000) / 100;
        if (h > 0) return h + ":" + pad2(m) + ":" + pad2(s) + "." + tenths;
        if (m > 0) return m + ":" + pad2(s) + "." + tenths;
        return s + "." + tenths;
    }
}
