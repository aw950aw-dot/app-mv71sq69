package com.generated.app;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileImageSource implements ByteSource {

    private final ParcelFileDescriptor pfd;
    private final FileChannel fileChannel;
    private final long size;

    public FileImageSource(ContentResolver resolver, Uri uri) throws IOException {
        this.pfd = resolver.openFileDescriptor(uri, "r");
        if (this.pfd == null) {
            throw new IOException("Could not open ParcelFileDescriptor for URI: " + uri);
        }
        this.size = this.pfd.getStatSize();
        
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(this.pfd.getFileDescriptor());
            this.fileChannel = fis.getChannel();
        } catch (Exception e) {
            this.pfd.close();
            throw e;
        }
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public int readAt(long offset, byte[] buf, int len) throws IOException {
        synchronized (fileChannel) {
            fileChannel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(buf, 0, len);
            int bytesRead = fileChannel.read(buffer);
            return bytesRead;
        }
    }

    @Override
    public void close() {
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (pfd != null) {
                pfd.close();
            }
        } catch (IOException ignored) {
        }
    }
}
