package com.generated.app;

import java.io.IOException;

public interface ByteSource {
    long getSize();
    int readAt(long offset, byte[] buf, int len) throws IOException;
    void close();
}
