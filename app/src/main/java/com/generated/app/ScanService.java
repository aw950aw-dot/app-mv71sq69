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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        dbHelper = new DatabaseHelper(this);
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
                stopSelf();
                return START_NOT_STICKY;
            }

            if (byteSource != null) {
                carver = new Carver();
                startForeground(NOTIFICATION_ID, createNotification("מחלץ קבצים...", 0));
                startScanThread();
            } else {
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
                    final List<RecoveredEntry> batchBuffer = new ArrayList<>();

                    FilesystemParser.parseFilesystem(byteSource, new FilesystemParser.ParserCallback() {
                        @Override
                        public void onBatchReady(List<RecoveredEntry> batch) {
                            // Handled via stream
                        }

                        @Override
                        public void onProgress(String currentFolder) {
                            while (isPaused && !isCancelled) {
                                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                            }
                        }
                    });

                    // Collect and insert entries directly in transactions of 1000 rows
                    // Re-running parse with batch transactions
                    final List<RecoveredEntry> allFsEntries = FilesystemParser.parseFilesystem(byteSource, new FilesystemParser.ParserCallback() {
                        @Override
                        public void onBatchReady(List<RecoveredEntry> batch) {}
                        @Override
                        public void onProgress(String currentFolder) {
                            while (isPaused && !isCancelled) {
                                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                            }
                            broadcastProgress(currentFolder, dbHelper.getTotalFilesCount() + dbHelper.getTotalFoldersCount());
                        }
                    });

                    // Insert in chunks of 1000 per rule 3
                    List<RecoveredEntry> chunk = new ArrayList<>();
                    for (RecoveredEntry entry : allFsEntries) {
                        chunk.add(entry);
                        if (chunk.size() >= 1000) {
                            dbHelper.insertEntriesInBatch(chunk);
                            chunk.clear();
                            broadcastProgress("שומר עסקאות במסד הנתונים...", dbHelper.getTotalFilesCount() + dbHelper.getTotalFoldersCount());
                        }
                    }
                    if (!chunk.isEmpty()) {
                        dbHelper.insertEntriesInBatch(chunk);
                    }

                    int totalFiles = dbHelper.getTotalFilesCount();
                    int totalFolders = dbHelper.getTotalFoldersCount();
                    DiagnosticReporter.setFilesFoldersCounts(totalFiles, totalFolders);
                    DiagnosticReporter.log("הסריקה הסתיימה בהצלחה. סה\"כ קבצים: " + totalFiles + ", תיקיות: " + totalFolders);

                    // If filesystem scan found zero files, run deep carve scan per rule
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
                            }

                            @Override
                            public void onProgress(long bytesScanned, long totalBytes) {
                                int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                                updateNotification("Filesystem scan found no files, running deep scan: " + percent + "%", percent);
                                broadcastProgress("סריקה עמוקה (Carving)", dbHelper.getTotalFilesCount());
                            }
                        });
                    }

                } catch (Exception e) {
                    DiagnosticReporter.logError(e, "Scan thread exception caught. Preserving results.");
                } finally {
                    int totalFiles = dbHelper.getTotalFilesCount();
                    int totalFolders = dbHelper.getTotalFoldersCount();
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

    private void broadcastProgress(String currentFolder, int count) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra("count", count);
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
        if (scanThread != null) {
            scanThread.interrupt();
        }
        if (byteSource != null) {
            byteSource.close();
        }
    }
}
