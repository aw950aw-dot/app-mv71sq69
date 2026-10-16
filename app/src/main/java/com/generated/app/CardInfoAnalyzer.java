package com.generated.app;

import java.io.IOException;

/**
 * Reads basic facts about a memory card: size, used space (FAT32) and read speed.
 * Read-only: it never writes to the card.
 */
public final class CardInfoAnalyzer {

    public static final class Info {
        public long sizeBytes;
        public boolean fat32;
        public long usedBytes = -1;     // -1 = unknown
        public long clusterBytes;
        public String serial = "";
        public double readSpeedBps;     // 0 = unknown
        public String note = "";
    }

    public interface Cancel {
        boolean isCancelled();
    }

    private static final int SECTOR = 512;
    private static final String CANCELLED = "cancelled";

    private CardInfoAnalyzer() {}

    public static Info analyze(ByteSource src, Cancel cancel) throws IOException {
        Info info = new Info();
        info.sizeBytes = src.getSize();

        byte[] mbr = new byte[SECTOR];
        readExact(src, 0, mbr, SECTOR, cancel);

        long volStart = -1;
        byte[] bs = null;

        if (looksLikeFat32Boot(mbr)) {
            volStart = 0;          // no partition table: the filesystem starts at sector 0
            bs = mbr;
        } else if (u8(mbr, 510) == 0x55 && u8(mbr, 511) == 0xAA) {
            for (int i = 0; i < 4 && bs == null; i++) {
                int base = 446 + 16 * i;
                int type = u8(mbr, base + 4);
                long start = u32(mbr, base + 8);
                long count = u32(mbr, base + 12);
                if (type == 0 || type == 0xEE || start <= 0 || count <= 0) continue;
                long off = start * SECTOR;
                if (off + SECTOR > info.sizeBytes) continue;
                byte[] cand = new byte[SECTOR];
                try {
                    readExact(src, off, cand, SECTOR, cancel);
                    if (looksLikeFat32Boot(cand)) {
                        volStart = off;
                        bs = cand;
                    } else {
                        long backupOff = off + 6L * SECTOR;   // backup boot sector of FAT32
                        if (backupOff + SECTOR <= info.sizeBytes) {
                            readExact(src, backupOff, cand, SECTOR, cancel);
                            if (looksLikeFat32Boot(cand)) {
                                volStart = off;
                                bs = cand;
                                info.note = "backup-boot";
                            }
                        }
                    }
                } catch (IOException e) {
                    if (isCancel(e)) throw e;
                }
            }
        }

        if (bs != null) {
            try {
                readFatUsage(src, info, bs, volStart, cancel);
            } catch (IOException e) {
                if (isCancel(e)) throw e;
                info.note = "fat-read-failed";
            }
        }

        measureSpeed(src, info, cancel);
        return info;
    }

    private static void readFatUsage(ByteSource src, Info info, byte[] bs, long volStart,
                                     Cancel cancel) throws IOException {
        int bps = u16(bs, 11);
        int spc = u8(bs, 13);
        long reserved = u16(bs, 14);
        long fats = u8(bs, 16);
        long totalSectors = u32(bs, 32);
        long spf = u32(bs, 36);

        long dataSectors = totalSectors - reserved - fats * spf;
        if (dataSectors <= 0) return;
        long clusters = dataSectors / spc;
        if (clusters < 1 || clusters > 0x0FFFFFF5L) return;
        long entries = clusters + 2;
        if (entries * 4L > spf * bps) return;      // the FAT is too small for this many clusters

        info.clusterBytes = (long) spc * bps;
        long serial = u32(bs, 67);
        info.serial = String.format("%04X-%04X", (serial >> 16) & 0xFFFF, serial & 0xFFFF);

        long fatOffset = volStart + reserved * bps;
        long fatBytes = entries * 4L;
        byte[] chunk = new byte[65536];
        long used = 0;
        long idx = 0;
        long pos = 0;
        while (pos < fatBytes) {
            int len = (int) Math.min(chunk.length, fatBytes - pos);
            int rd = (len + SECTOR - 1) / SECTOR * SECTOR;
            readExact(src, fatOffset + pos, chunk, rd, cancel);
            for (int o = 0; o < len; o += 4) {
                if (idx >= 2 && (u32(chunk, o) & 0x0FFFFFFFL) != 0) used++;
                idx++;
            }
            pos += len;
        }

        info.fat32 = true;
        info.usedBytes = Math.min(used * info.clusterBytes, dataSectors * bps);
    }

    private static void measureSpeed(ByteSource src, Info info, Cancel cancel) throws IOException {
        final int chunkLen = 65536;
        final long testBytes = 8L * 1024 * 1024;
        if (info.sizeBytes < 4 * testBytes) return;
        long start = (info.sizeBytes / 2) / 4096 * 4096;   // the middle of the card
        byte[] buf = new byte[chunkLen];
        long got = 0;
        long t0 = System.nanoTime();
        try {
            while (got < testBytes) {
                readExact(src, start + got, buf, chunkLen, cancel);
                got += chunkLen;
            }
        } catch (IOException e) {
            if (isCancel(e)) throw e;
            return;
        }
        double sec = (System.nanoTime() - t0) / 1e9;
        if (sec > 0) info.readSpeedBps = got / sec;
    }

    private static boolean looksLikeFat32Boot(byte[] b) {
        if (u8(b, 510) != 0x55 || u8(b, 511) != 0xAA) return false;
        int bps = u16(b, 11);
        if (bps != 512 && bps != 1024 && bps != 2048 && bps != 4096) return false;
        int spc = u8(b, 13);
        if (spc == 0 || spc > 128 || (spc & (spc - 1)) != 0) return false;
        if (u16(b, 14) == 0) return false;
        int fats = u8(b, 16);
        if (fats < 1 || fats > 2) return false;
        if (u16(b, 17) != 0 || u16(b, 22) != 0) return false;
        return u32(b, 36) > 0;
    }

    /** Reads exactly len bytes (ByteSource.readAt always fills the buffer from index 0). */
    private static void readExact(ByteSource src, long offset, byte[] buf, int len,
                                  Cancel cancel) throws IOException {
        int done = 0;
        byte[] tmp = null;
        while (done < len) {
            if (cancel != null && cancel.isCancelled()) throw new IOException(CANCELLED);
            int want = len - done;
            byte[] target = buf;
            if (done > 0) {
                if (tmp == null) tmp = new byte[len];
                target = tmp;
            }
            int n = src.readAt(offset + done, target, want);
            if (n <= 0) throw new IOException("read failed at offset " + (offset + done));
            if (done > 0) System.arraycopy(target, 0, buf, done, n);
            done += n;
        }
    }

    public static boolean isCancel(IOException e) {
        return CANCELLED.equals(e.getMessage());
    }

    private static int u8(byte[] b, int o) {
        return b[o] & 0xFF;
    }

    private static int u16(byte[] b, int o) {
        return u8(b, o) | (u8(b, o + 1) << 8);
    }

    private static long u32(byte[] b, int o) {
        return (u16(b, o) & 0xFFFFL) | ((long) u16(b, o + 2) << 16);
    }
}
