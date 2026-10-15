package com.generated.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ScanService extends Service {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    public static final String ACTION_PROGRESS = "com.generated.app.ACTION_PROGRESS";
    public static final String ACTION_COMPLETED = "com.generated.app.ACTION_COMPLETED";
    public static final String ACTION_PAUSE = "com.generated.app.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.generated.app.ACTION_RESUME";
    public static final String ACTION_CANCEL = "com.generated.app.ACTION_CANCEL";

    private static final String CHANNEL_ID = "scan_service_channel";
    private static final int NOTIFICATION_ID = 101;

    private ByteSource byteSource;
    private Carver carver;
    private Thread scanThread;
    private DatabaseHelper dbHelper;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;

    private static final AtomicInteger filesCount = new AtomicInteger(0);
    private static final AtomicInteger foldersCount = new AtomicInteger(0);
    private static final AtomicLong totalBytesScanned = new AtomicLong(0);
    private static volatile String currentFolderPath = "/";
    private static volatile boolean isScanRunning = false;

    private long lastBroadcastTime = 0;
    private long lastNotificationUpdate = 0;
    private long lastHeartbeatUpdate = 0;
    private long scanStartTime = 0;

    public static boolean isScanRunning() {
        return isScanRunning;
    }

    public static int getFilesCount() {
        return filesCount.get();
    }

    public static int getFoldersCount() {
        return foldersCount.get();
    }

    public static String getCurrentFolderPath() {
        return currentFolderPath;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        dbHelper = DatabaseHelper.getInstance(this);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SDRescue:ScanWakeLock");
            wakeLock.acquire(4 * 60 * 60 * 1000L);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                String action = intent.getAction();
                if (ACTION_PAUSE.equals(action)) {
                    isPaused = true;
                    updateNotificationIndeterminate("הסריקה מושהית", 0);
                    return START_NOT_STICKY;
                } else if (ACTION_RESUME.equals(action)) {
                    isPaused = false;
                    updateNotificationIndeterminate("סורק את הכרטיס", 0);
                    return START_NOT_STICKY;
                } else if (ACTION_CANCEL.equals(action)) {
                    isCancelled = true;
                    isScanRunning = false;
                    dbHelper.updateJobState("scan", "CANCELLED", scanStartTime, System.currentTimeMillis(), foldersCount.get(), filesCount.get(), totalBytesScanned.get());
                    ServiceState.getInstance().update(ServiceState.Phase.CANCELLED, filesCount.get(), foldersCount.get(), totalBytesScanned.get(), currentFolderPath, 0, "", "סריקה בוטלה");
                    if (scanThread != null) scanThread.interrupt();
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }

            if (intent != null && scanThread == null) {
                Uri uri = intent.getParcelableExtra(EXTRA_URI);
                String usbName = intent.getStringExtra(EXTRA_USB_DEVICE_NAME);

                DiagnosticReporter.clear();
                try {
                    dbHelper.clearDatabase();
                } catch (Exception dbEx) {
                    DiagnosticReporter.logError(dbEx, "Failed to clear database on startup");
                }

                filesCount.set(0);
                foldersCount.set(0);
                totalBytesScanned.set(0);
                currentFolderPath = "/";
                isScanRunning = true;
                scanStartTime = System.currentTimeMillis();

                dbHelper.updateJobState("scan", "RUNNING", scanStartTime, scanStartTime, 0, 0, 0);
                ServiceState.getInstance().update(ServiceState.Phase.SCANNING, 0, 0, 0, "/", 0, "מתחיל סריקה...", "");

                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(4 * 60 * 60 * 1000L);
                }

                try {
                    if (uri != null) {
                        byteSource = new FileImageSource(getContentResolver(), uri);
                        DiagnosticReporter.log("Opened Content Uri successfully.");
                    } else if (usbName != null) {
                        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                        UsbDevice target = null;
                        for (UsbDevice d : deviceList.values()) {
                            if (d.getDeviceName().equals(usbName)) {
                                target = d;
                                break;
                            }
                        }
                        if (target != null) {
                            byteSource = new UsbMassStorageSource(this, target);
                            DiagnosticReporter.log("Opened UsbMassStorageSource successfully for device: " + usbName);
                        }
                    }
                } catch (Exception e) {
                    DiagnosticReporter.log("FATAL: Failed to open source: " + e.getMessage());
                    DiagnosticReporter.logError(e, "Failed to open scan source");
                    isScanRunning = false;
                    dbHelper.updateJobState("scan", "INTERRUPTED", scanStartTime, System.currentTimeMillis(), foldersCount.get(), filesCount.get(), totalBytesScanned.get());
                    ServiceState.getInstance().update(ServiceState.Phase.IDLE, 0, 0, 0, "/", 0, "", e.getMessage());
                    stopSelf();
                    return START_NOT_STICKY;
                }

                if (byteSource != null) {
                    carver = new Carver();
                    startForeground(NOTIFICATION_ID, createNotificationIndeterminate("סורק את הכרטיס", 0));
                    startScanThread();
                } else {
                    isScanRunning = false;
                    dbHelper.updateJobState("scan", "INTERRUPTED", scanStartTime, System.currentTimeMillis(), foldersCount.get(), filesCount.get(), totalBytesScanned.get());
                    stopSelf();
                }
            }
        } catch (Throwable t) {
            DiagnosticReporter.logError(t, "Exception in ScanService.onStartCommand");
            isScanRunning = false;
            dbHelper.updateJobState("scan", "INTERRUPTED", scanStartTime, System.currentTimeMillis(), foldersCount.get(), filesCount.get(), totalBytesScanned.get());
            ServiceState.getInstance().update(ServiceState.Phase.IDLE, 0, 0, 0, "/", 0, "", t.getMessage());
            stopSelf();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SD Rescue Scan Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotificationIndeterminate(String title, int dummy) {
        Intent notificationIntent = new Intent(this, ScanActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, ScanService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        long elapsedSec = (System.currentTimeMillis() - scanStartTime) / 1000;
        String elapsedStr = String.format(Locale.getDefault(), "%02d:%02d", elapsedSec / 60, elapsedSec % 60);
        String contentText = String.format(Locale.getDefault(), "נמצאו %d תיקיות ו-%d קבצים · זמן שעבר: %s", foldersCount.get(), filesCount.get(), elapsedStr);

        builder.setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ביטול", cancelPendingIntent);

        return builder.build();
    }

    private Notification createNotificationDeterminate(int percent, double speedMbSec, long etaSec) {
        Intent notificationIntent = new Intent(this, ScanActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, ScanService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        long etaMin = Math.max(1, etaSec / 60);
        String subText = String.format(Locale.getDefault(), "%.1f MB/s · נשארו כ-%d דקות", speedMbSec, etaMin);

        builder.setContentTitle("סורק את הכרטיס (סריקה עמוקה)")
                .setContentText(String.format(Locale.getDefault(), "התקדמות: %d%%", percent))
                .setSubText(subText)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ביטול", cancelPendingIntent);

        return builder.build();
    }

    private void updateNotificationIndeterminate(String title, int dummy) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotificationIndeterminate(title, dummy));
        }
    }

    private void updateNotificationDeterminate(int percent, double speedMbSec, long etaSec) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotificationDeterminate(percent, speedMbSec, etaSec));
        }
    }

    private void startScanThread() {
        scanThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FilesystemParser.parseFilesystem(byteSource, new FilesystemParser.ParserCallback() {
                        @Override
                        public void onBatchReady(List<RecoveredEntry> batch) {
                            if (batch != null && !batch.isEmpty()) {
                                dbHelper.insertEntriesInBatch(batch);
                                for (RecoveredEntry entry : batch) {
                                    if (entry.isDirectory()) {
                                        foldersCount.incrementAndGet();
                                    } else {
                                        filesCount.incrementAndGet();
                                    }
                                }
                                checkAndRecordHeartbeat();
                                throttleBroadcast(currentFolderPath, 0, "");
                                throttleNotificationIndeterminate();
                            }
                        }

                        @Override
                        public void onProgress(String currentFolder) {
                            while (isPaused && !isCancelled) {
                                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                            }
                            if (currentFolder != null) {
                                currentFolderPath = currentFolder;
                            }
                            checkAndRecordHeartbeat();
                            throttleBroadcast(currentFolderPath, 0, "");
                            throttleNotificationIndeterminate();
                        }
                    });

                    int totalFiles = dbHelper.getTotalFilesCount();
                    int totalFolders = dbHelper.getTotalFoldersCount();
                    filesCount.set(totalFiles);
                    foldersCount.set(totalFolders);
                    DiagnosticReporter.setFilesFoldersCounts(totalFiles, totalFolders);
                    DiagnosticReporter.log("הסריקה הסתיימה בהצלחה. סה\"כ קבצים: " + totalFiles + ", תיקיות: " + totalFolders);

                    if (totalFiles == 0) {
                        DiagnosticReporter.log("Filesystem scan found no files, running deep scan");
                        final long deepScanStartTime = System.currentTimeMillis();

                        carver.carve(byteSource, new CarverCallback() {
                            @Override
                            public void onFileFound(CarvedFile carvedFile) {
                                while (isPaused && !isCancelled) {
                                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                                }
                                if (isCancelled) return;

                                String typeFolderName = FileType.safeName(carvedFile.getType());
                                String fileName = "carved_" + carvedFile.getOffset() + "." + typeFolderName.toLowerCase();
                                long assignedId = System.nanoTime() & 0x7FFFFFFFFFFL;
                                long rootId = dbHelper.getRootId();
                                RecoveredEntry entry = new RecoveredEntry(
                                        assignedId,
                                        rootId,
                                        fileName,
                                        "/Carved files/" + typeFolderName,
                                        carvedFile.getOffset(),
                                        carvedFile.getLength(),
                                        carvedFile.getType(),
                                        false,
                                        false,
                                        System.currentTimeMillis(),
                                        fileName.getBytes(),
                                        0
                                );
                                List<RecoveredEntry> single = new ArrayList<>();
                                single.add(entry);
                                dbHelper.insertEntriesInBatch(single);
                                filesCount.incrementAndGet();
                                checkAndRecordHeartbeat();
                                throttleBroadcast("/Carved files/" + typeFolderName, 0, "");
                            }

                            @Override
                            public void onProgress(long bytesScanned, long totalBytes) {
                                totalBytesScanned.set(bytesScanned);
                                int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                                long elapsedMs = System.currentTimeMillis() - deepScanStartTime;
                                double speedBytesPerSec = elapsedMs > 500 ? (bytesScanned * 1000.0) / elapsedMs : 0;
                                double speedMbSec = speedBytesPerSec / (1024.0 * 1024.0);
                                long remainingBytes = totalBytes - bytesScanned;
                                long etaSec = speedBytesPerSec > 1024 ? (long) (remainingBytes / speedBytesPerSec) : 0;

                                checkAndRecordHeartbeat();
                                throttleNotificationDeterminate(percent, speedMbSec, etaSec);
                                ServiceState.getInstance().update(ServiceState.Phase.SCANNING, filesCount.get(), foldersCount.get(), bytesScanned, "סריקה עמוקה (Carving)", percent, etaSec + "s", "");
                                broadcastProgress("סריקה עמוקה (Carving)", filesCount.get(), foldersCount.get());
                            }
                        });
                    }

                    dbHelper.computeAndStoreFolderTotals();
                    dbHelper.updateJobState("scan", "COMPLETED", scanStartTime, System.currentTimeMillis(), foldersCount.get(), filesCount.get(), totalBytesScanned.get());

                } catch (Exception e) {
                    DiagnosticReporter.logError(e, "Scan thread exception caught.");
                    dbHelper.updateJobState("scan", "INTERRUPTED", scanStartTime, System.currentTimeMillis(), foldersCount.get(), filesCount.get(), totalBytesScanned.get());
                    ServiceState.getInstance().update(ServiceState.Phase.IDLE, filesCount.get(), foldersCount.get(), totalBytesScanned.get(), currentFolderPath, 0, "", e.getMessage());
                } finally {
                    isScanRunning = false;
                    int totalFiles = dbHelper.getTotalFilesCount();
                    int totalFolders = dbHelper.getTotalFoldersCount();
                    filesCount.set(totalFiles);
                    foldersCount.set(totalFolders);
                    DiagnosticReporter.setFilesFoldersCounts(totalFiles, totalFolders);
                    ServiceState.getInstance().update(ServiceState.Phase.SCAN_COMPLETED, totalFiles, totalFolders, totalBytesScanned.get(), currentFolderPath, 100, "", "");
                    broadcastCompleted(totalFiles);
                    if (byteSource != null) {
                        byteSource.close();
                    }

                    if (wakeLock != null && wakeLock.isHeld()) {
                        try {
                            wakeLock.release();
                        } catch (Exception ignored) {}
                    }

                    stopSelf();
                }
            }
        });
        scanThread.start();
    }

    private void checkAndRecordHeartbeat() {
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatUpdate >= 2000) {
            lastHeartbeatUpdate = now;
            dbHelper.updateJobState("scan", "RUNNING", scanStartTime, now, foldersCount.get(), filesCount.get(), totalBytesScanned.get());
        }
    }

    private synchronized void throttleBroadcast(String currentFolder, int percent, String eta) {
        long now = System.currentTimeMillis();
        if (now - lastBroadcastTime >= 500) {
            lastBroadcastTime = now;
            ServiceState.getInstance().update(ServiceState.Phase.SCANNING, filesCount.get(), foldersCount.get(), totalBytesScanned.get(), currentFolder, percent, eta, "");
            broadcastProgress(currentFolder, filesCount.get(), foldersCount.get());
        }
    }

    private synchronized void throttleNotificationIndeterminate() {
        long now = System.currentTimeMillis();
        if (now - lastNotificationUpdate >= 500) {
            lastNotificationUpdate = now;
            updateNotificationIndeterminate("סורק את הכרטיס", 0);
        }
    }

    private synchronized void throttleNotificationDeterminate(int percent, double speedMbSec, long etaSec) {
        long now = System.currentTimeMillis();
        if (now - lastNotificationUpdate >= 500) {
            lastNotificationUpdate = now;
            updateNotificationDeterminate(percent, speedMbSec, etaSec);
        }
    }

    private void broadcastProgress(String currentFolder, int files, int folders) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra("files_count", files);
        intent.putExtra("folders_count", folders);
        intent.putExtra("folder", currentFolder);
        sendBroadcast(intent);
    }

    private void broadcastCompleted(int count) {
        Intent intent = new Intent(ACTION_COMPLETED);
        intent.putExtra("count", count);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCancelled = true;
        isScanRunning = false;
        if (scanThread != null) {
            scanThread.interrupt();
        }
        if (byteSource != null) {
            byteSource.close();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }
}
