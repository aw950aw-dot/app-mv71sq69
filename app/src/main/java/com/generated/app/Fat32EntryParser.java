package com.generated.app;

import java.nio.charset.Charset;
import java.util.HashMap;

public class Fat32EntryParser {
    private final Charset shortCharset;
    private boolean ended = false;
    private final HashMap<Integer, String> parts = new HashMap<>();
    private int expected = 0;
    private int lfnSum = -1;

    public Fat32EntryParser(Charset shortCharset) {
        this.shortCharset = shortCharset;
    }

    public boolean isEnded() {
        return ended;
    }

    private int u8(byte[] b, int o) {
        return b[o] & 0xFF;
    }

    private int u16(byte[] b, int o) {
        return u8(b, o) | (u8(b, o + 1) << 8);
    }

    private long u32(byte[] b, int o) {
        return u16(b, o) | ((long) u16(b, o + 2) << 16);
    }

    private void reset() {
        parts.clear();
        expected = 0;
        lfnSum = -1;
    }

    private int checksum(byte[] b, int o) {
        int s = 0;
        for (int i = 0; i < 11; i++) {
            s = (((s & 1) << 7) + (s >> 1) + u8(b, o + i)) & 0xFF;
        }
        return s;
    }

    public static class Item {
        public final String name;
        public final boolean isDir;
        public final long firstCluster;
        public final long size;
        public final int dosDate;
        public final int dosTime;
        public final boolean hadLfn;

        public Item(String name, boolean isDir, long firstCluster, long size, int dosDate, int dosTime, boolean hadLfn) {
            this.name = name;
            this.isDir = isDir;
            this.firstCluster = firstCluster;
            this.size = size;
            this.dosDate = dosDate;
            this.dosTime = dosTime;
            this.hadLfn = hadLfn;
        }
    }

    public Item feed(byte[] buf, int off) {
        int first = u8(buf, off);
        if (first == 0x00) {
            ended = true;
            return null;
        }
        if (first == 0xE5) {
            reset();
            return null;
        }
        int attr = u8(buf, off + 11);
        if ((attr & 0x3F) == 0x0F) {
            int seq = first & 0x1F;
            if ((first & 0x40) != 0) {
                reset();
                expected = seq;
                lfnSum = u8(buf, off + 13);
            }
            if (seq >= 1 && seq <= 20) {
                StringBuilder sb = new StringBuilder();
                int[] indices = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
                for (int o : indices) {
                    int c = u16(buf, off + o);
                    if (c == 0x0000 || c == 0xFFFF) break;
                    sb.append((char) c);
                }
                parts.put(seq, sb.toString());
            }
            return null;
        }
        if ((attr & 0x08) != 0) {
            reset();
            return null;
        }
        String name = null;
        boolean hadLfn = false;
        if (expected >= 1 && expected <= 20 && lfnSum == checksum(buf, off)) {
            boolean allPresent = true;
            for (int i = 1; i <= expected; i++) {
                if (!parts.containsKey(i)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                StringBuilder fullSb = new StringBuilder();
                for (int i = 1; i <= expected; i++) {
                    fullSb.append(parts.get(i));
                }
                name = fullSb.toString();
                hadLfn = true;
            }
        }
        if (name == null) {
            byte[] raw = new byte[11];
            System.arraycopy(buf, off, raw, 0, 11);
            if (u8(raw, 0) == 0x05) {
                raw[0] = (byte) 0xE5;
            }
            String base = new String(raw, 0, 8, shortCharset).trim();
            String ext = new String(raw, 8, 3, shortCharset).trim();
            int nt = u8(buf, off + 12);
            String b2 = ((nt & 0x08) != 0) ? base.toLowerCase() : base;
            String e2 = ((nt & 0x10) != 0) ? ext.toLowerCase() : ext;
            name = e2.isEmpty() ? b2 : b2 + "." + e2;
        }
        reset();
        if (name.equals(".") || name.equals("..")) return null;

        // Rule 6: First cluster is ALWAYS (high word << 16) | low word
        long cluster = ((long) u16(buf, off + 20) << 16) | u16(buf, off + 26);
        long size = u32(buf, off + 28);
        int dosDate = u16(buf, off + 24);
        int dosTime = u16(buf, off + 22);

        return new Item(name, (attr & 0x10) != 0, cluster, size, dosDate, dosTime, hadLfn);
    }
}
