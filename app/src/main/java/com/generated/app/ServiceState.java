package com.generated.app;

public class ServiceState {
    public enum Phase {
        IDLE,
        SCANNING,
        SCAN_COMPLETED,
        RESTORING,
        RESTORE_COMPLETED,
        CANCELLED
    }

    private Phase phase = Phase.IDLE;
    private int filesCount = 0;
    private int foldersCount = 0;
    private long totalBytesScanned = 0;
    private String currentFolder = "/";
    private int percent = 0;
    private String etaText = "";
    private String errorText = "";

    private static final ServiceState instance = new ServiceState();

    public static ServiceState getInstance() {
        return instance;
    }

    public synchronized void update(Phase phase, int files, int folders, long bytes, String folder, int percent, String eta, String error) {
        if (phase != null) this.phase = phase;
        this.filesCount = files;
        this.foldersCount = folders;
        this.totalBytesScanned = bytes;
        if (folder != null) this.currentFolder = folder;
        this.percent = percent;
        if (eta != null) this.etaText = eta;
        if (error != null) this.errorText = error;
    }

    public synchronized Phase getPhase() { return phase; }
    public synchronized int getFilesCount() { return filesCount; }
    public synchronized int getFoldersCount() { return foldersCount; }
    public synchronized long getTotalBytesScanned() { return totalBytesScanned; }
    public synchronized String getCurrentFolder() { return currentFolder; }
    public synchronized int getPercent() { return percent; }
    public synchronized String getEtaText() { return etaText; }
    public synchronized String getErrorText() { return errorText; }
}
