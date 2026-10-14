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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;

    private static final AtomicInteger filesCount = new AtomicInteger(0);
    private static final AtomicInteger foldersCount = new AtomicInteger(0);
    private static final AtomicLong totalBytesScanned = new AtomicLong(0);
    private static final AtomicInteger errorsCount = new AtomicInteger(0);
    private static volatile String currentFolderPath = "/";
    private static volatile boolean isScanRunning = false;

    private long lastBroadcastTime = 0;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PAUSE.equals(action)) {
                isPaused = true;
                updateNotification("הסריקה מושהית...", 0);
                return START_NOT_STICKY;
            } else if (ACTION_RESUME.equals(action)) {
                isPaused = false;
                updateNotification("מחלץ קבצים...", 0);
                return START_NOT_STICKY;
            } else if (ACTION_CANCEL.equals(action)) {
                isCancelled = true;
                isScanRunning = false;
                if (scanThread != null) scanThread.interrupt();
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent != null && scanThread == null) {
            Uri uri = intent.getParcelableExtra(EXTRA_URI);
            String usbName = intent.getStringExtra(EXTRA_USB_DEVICE_NAME);

            DiagnosticReporter.clear();
            dbHelper.clearDatabase();
            filesCount.set(0);
            foldersCount.set(0);
            totalBytesScanned.set(0);
            errorsCount.set(0);
            currentFolderPath = "/";
            isScanRunning = true;

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
                isScanRunning = false;
                stopSelf();
                return START_NOT_STICKY;
            }

            if (byteSource != null) {
                carver = new Carver();
                startForeground(NOTIFICATION_ID, createNotification("מחלץ קבצים...", 0));
                startScanThread();
            } else {
                isScanRunning = false;
                stopSelf();
            }
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
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text, int progress) {
        Intent notificationIntent = new Intent(this, ScanActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentIntent(pendingIntent);

        if (progress > 0) {
            builder.setProgress(100, progress, false);
        }

        return builder.build();
    }

    private void updateNotification(String text, int progress) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text, progress));
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
                                throttleBroadcast(currentFolderPath);
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
                            throttleBroadcast(currentFolderPath);
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
                        carver.carve(byteSource, new CarverCallback() {
                            @Override
                            public void onFileFound(CarvedFile carvedFile) {
                                while (isPaused && !isCancelled) {
                                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                                }
                                if (isCancelled) return;

                                String typeFolderName = FileType.safeName(carvedFile.getType());
                                String fileName = "carved_" + carvedFile.getOffset() + "." + typeFolderName.toLowerCase();
                                RecoveredEntry entry = new RecoveredEntry(
                                        fileName,
                                        "/Carved files/" + typeFolderName,
                                        carvedFile.getOffset(),
                                        carvedFile.getLength(),
                                        carvedFile.getType(),
                                        false,
                                        false,
                                        System.currentTimeMillis(),
                                        fileName.getBytes()
                                );
                                List<RecoveredEntry> single = new ArrayList<>();
                                single.add(entry);
                                dbHelper.insertEntriesInBatch(single);
                                filesCount.incrementAndGet();
                                throttleBroadcast("/Carved files/" + typeFolderName);
                            }

                            @Override
                            public void onProgress(long bytesScanned, long totalBytes) {
                                int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                                updateNotification("סריקה עמוקה: " + percent + "%", percent);
                                throttleBroadcast("סריקה עמוקה (Carving)");
                            }
                        });
                    }

                } catch (Exception e) {
                    DiagnosticReporter.logError(e, "Scan thread exception caught.");
                } finally {
                    isScanRunning = false;
                    int totalFiles = dbHelper.getTotalFilesCount();
                    int totalFolders = dbHelper.getTotalFoldersCount();
                    filesCount.set(totalFiles);
                    foldersCount.set(totalFolders);
                    DiagnosticReporter.setFilesFoldersCounts(totalFiles, totalFolders);
                    broadcastCompleted(totalFiles);
                    if (byteSource != null) {
                        byteSource.close();
                    }
                    stopSelf();
                }
            }
        });
        scanThread.start();
    }

    private synchronized void throttleBroadcast(String currentFolder) {
        long now = System.currentTimeMillis();
        // Throttle to at most 2 per second (500 ms interval)
        if (now - lastBroadcastTime >= 500) {
            lastBroadcastTime = now;
            broadcastProgress(currentFolder, filesCount.get(), foldersCount.get());
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
    }
}
