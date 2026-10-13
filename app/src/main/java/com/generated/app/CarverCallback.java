package com.generated.app;

public interface CarverCallback {
    void onFileFound(CarvedFile carvedFile);
    void onProgress(long bytesScanned, long totalBytes);
}
