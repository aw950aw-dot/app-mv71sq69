package com.generated.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads file data from a FAT32 volume by FOLLOWING THE CLUSTER CHAIN of the file.
 * A file is often split into several pieces (fragments) that are not next to each other on the card;
 * reading it as one continuous block gives the first piece and then data that belongs to other files.
 */
public final class Fat32Volume {

    public interface Cancel {
        boolean isCancelled();
    }

    private final long clusterRegionStart;   // byte offset of cluster number 2
    private final long clusterSize;
    private final long maxCluster;           // highest valid cluster number, 0 = unknown
    private final FatLookup fat;

    private Fat32Volume(long clusterRegionStart, long clusterSize, long maxCluster, FatLookup fat) {
        this.clusterRegionStart = clusterRegionStart;
        this.clusterSize = clusterSize;
        this.maxCluster = maxCluster;
        this.fat = fat;
    }

    /** Finds the FAT32 volume the same way the scan did. Returns null when the source is not FAT32. */
    public static Fat32Volume open(ByteSource source) throws IOException {
        long totalSize = source.getSize();
        if (totalSize < 512) return null;
        byte[] sector0 = new byte[512];
        if (source.readAt(0, sector0, 512) < 512) return null;

        long startLba = 0;
        if ((sector0[510] & 0xFF) == 0x55 && (sector0[511] & 0xFF) == 0xAA) {
            for (int p = 0; p < 4; p++) {
                int o = 446 + p * 16;
                int type = sector0[o + 4] & 0xFF;
                long start = le32(sector0, o + 8);
                long size = le32(sector0, o + 12);
                if (type != 0 && type != 0xEE && start > 0 && size > 0) {
                    startLba = start;
                    break;
                }
            }
        }
        long volumeStart = startLba * 512;

        byte[] boot = new byte[512];
        boolean ok = volumeStart < totalSize && source.readAt(volumeStart, boot, 512) == 512 && isValidFat32(boot);
        if (!ok) {
            long backup = volumeStart + 6L * 512;      // backup boot sector
            ok = backup < totalSize && source.readAt(backup, boot, 512) == 512 && isValidFat32(boot);
        }
        if (!ok) return null;

        int bps = le16(boot, 11);
        int spc = boot[13] & 0xFF;
        long reserved = le16(boot, 14);
        long numFats = boot[16] & 0xFF;
        long spf = le32(boot, 36);
        long totalSectors = le32(boot, 32);
        if (totalSectors == 0) totalSectors = le16(boot, 19);

        long fatStart = volumeStart + reserved * bps;
        long clusterRegionStart = volumeStart + (reserved + numFats * spf) * bps;
        long clusterSize = (long) spc * bps;
        long maxCluster = 0;
        if (totalSectors > 0) {
            long dataSectors = totalSectors - reserved - numFats * spf;
            if (dataSectors > 0) maxCluster = dataSectors / spc + 1;
        }

        long fatBytes = spf * bps;
        int toLoad = (int) Math.min(fatBytes, 64L * 1024 * 1024);
        byte[] table = new byte[toLoad];
        readBestEffort(source, fatStart, table, toLoad);
        return new Fat32Volume(clusterRegionStart, clusterSize, maxCluster, new FatLookup(source, fatStart, table, fatBytes));
    }

    /**
     * Writes the whole file to out, following the cluster chain, and returns the number of bytes written.
     * Throws if the chain ends before the file size is reached.
     */
    public long copyFile(ByteSource source, long firstCluster, long size, OutputStream out, Cancel cancel) throws IOException {
        if (size <= 0) return 0;
        long needed = (size + clusterSize - 1) / clusterSize;
        byte[] buf = new byte[65536];
        long written = 0;
        long done = 0;
        long c = firstCluster;

        while (done < needed) {
            if (c < 2 || c >= 0x0FFFFFF8L || (maxCluster > 0 && c > maxCluster)) {
                throw new IOException("Chain shorter than size: the cluster chain ended after " + done
                        + " of " + needed + " clusters (file is incomplete or the FAT is damaged)");
            }
            // gather a run of clusters that are next to each other on the card
            long runStart = c;
            long runLen = 1;
            long next = fat.next(c);
            while (done + runLen < needed && next == runStart + runLen) {
                runLen++;
                next = fat.next(runStart + runLen - 1);
            }

            long runBytes = Math.min(runLen * clusterSize, size - written);
            long offset = clusterRegionStart + (runStart - 2) * clusterSize;
            long pos = 0;
            while (pos < runBytes) {
                if (cancel != null && cancel.isCancelled()) throw new IOException("cancelled");
                int want = (int) Math.min(buf.length, runBytes - pos);
                int n = source.readAt(offset + pos, buf, want);
                if (n <= 0) throw new IOException("Read failed at offset 0x" + Long.toHexString(offset + pos));
                out.write(buf, 0, n);
                pos += n;
                written += n;
            }
            done += runLen;
            c = next;
        }
        return written;
    }

    private static boolean isValidFat32(byte[] b) {
        if ((b[510] & 0xFF) != 0x55 || (b[511] & 0xFF) != 0xAA) return false;
        int bps = le16(b, 11);
        int spc = b[13] & 0xFF;
        int reserved = le16(b, 14);
        int fats = b[16] & 0xFF;
        long spf = le32(b, 36);
        long rootCluster = le32(b, 44) & 0xFFFF;
        return (bps == 512 || bps == 1024 || bps == 2048 || bps == 4096)
                && spc > 0 && reserved > 0 && fats > 0 && spf > 0 && rootCluster >= 2;
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static long le32(byte[] b, int o) {
        return (le16(b, o) & 0xFFFFL) | ((long) le16(b, o + 2) << 16);
    }

    /** ByteSource.readAt returns at most 64 KB per call and fills the buffer from index 0, so loop. */
    private static void readBestEffort(ByteSource source, long offset, byte[] dest, int len) {
        byte[] piece = new byte[65536];
        int done = 0;
        while (done < len) {
            int want = Math.min(piece.length, len - done);
            int n;
            try {
                n = source.readAt(offset + done, piece, want);
            } catch (IOException e) {
                break;
            }
            if (n <= 0) break;
            System.arraycopy(piece, 0, dest, done, n);
            done += n;
        }
    }

    /** FAT entry lookup: from the loaded table, or (for a very large FAT) straight from the card. */
    private static final class FatLookup {
        private final ByteSource source;
        private final long fatStart;
        private final byte[] table;
        private final long fatBytes;
        private final byte[] block = new byte[4096];
        private long blockStart = -1;

        FatLookup(ByteSource source, long fatStart, byte[] table, long fatBytes) {
            this.source = source;
            this.fatStart = fatStart;
            this.table = table;
            this.fatBytes = fatBytes;
        }

        long next(long cluster) {
            long index = cluster * 4;
            if (index + 4 <= table.length) {
                return ByteBuffer.wrap(table, (int) index, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFFL;
            }
            if (index + 4 > fatBytes) return 0x0FFFFFFF;
            long start = index / 4096 * 4096;
            try {
                if (start != blockStart) {
                    int want = (int) Math.min(4096, fatBytes - start);
                    int n = source.readAt(fatStart + start, block, want);
                    if (n < want) return 0x0FFFFFFF;
                    blockStart = start;
                }
                return ByteBuffer.wrap(block, (int) (index - start), 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x0FFFFFFFL;
            } catch (IOException e) {
                return 0x0FFFFFFF;
            }
        }
    }
}
