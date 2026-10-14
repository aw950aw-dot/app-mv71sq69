package com.generated.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class RestoreService extends Service {

    public static final String EXTRA_TREE_URI = "extra_tree_uri";
    public static final String EXTRA_SAVE_ALL = "extra_save_all";
    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";

    public static final String ACTION_PROGRESS = "com.generated.app.RESTORE_PROGRESS";
    public static final String ACTION_COMPLETED = "com.generated.app.RESTORE_COMPLETED";
    public static final String ACTION_CANCEL = "com.generated.app.RESTORE_CANCEL";

    private static final String CHANNEL_ACTIVE_ID = "restore_service_channel";
    private static final String CHANNEL_COMPLETED_ID = "restore_completed_channel";
    private static final int NOTIFICATION_ID = 202;

    private Thread restoreThread;
    private PowerManager.WakeLock wakeLock;
    private DatabaseHelper dbHelper;
    private ByteSource byteSource;
    private volatile boolean isCancelled = false;

    private static volatile boolean isRestoreRunning = false;
    private static int restoredFilesCount = 0;
    private static int failedFilesCount = 0;
    private static int totalFilesCount = 0;
    private static long totalBytesCount = 0;
    private static long currentBytesRestored = 0;
    private static String currentFileName = "";
    private static int currentPercent = 0;
    private static String currentEtaText = "מחשב זמן נותר...";

    public static boolean isRestoreRunning() {
        return isRestoreRunning;
    }

    public static int getRestoredFilesCount() { return restoredFilesCount; }
    public static int getFailedFilesCount() { return failedFilesCount; }
    public static int getTotalFilesCount() { return totalFilesCount; }
    public static long getTotalBytesCount() { return totalBytesCount; }
    public static long getCurrentBytesRestored() { return currentBytesRestored; }
    public static String getCurrentFileName() { return currentFileName; }
    public static int getCurrentPercent() { return currentPercent; }
    public static String getCurrentEtaText() { return currentEtaText; }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        dbHelper = DatabaseHelper.getInstance(this);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SDRescue:RestoreWakeLock");
            wakeLock.acquire(4 * 60 * 60 * 1000L); // 4 hours max
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CANCEL.equals(action)) {
                isCancelled = true;
                isRestoreRunning = false;
                if (restoreThread != null) restoreThread.interrupt();
                stopSelf();
                return START_NOT_STICKY;
            }

            Uri treeUri = intent.getParcelableExtra(EXTRA_TREE_URI);
            boolean saveAll = intent.getBooleanExtra(EXTRA_SAVE_ALL, true);
            Uri sourceUri = intent.getParcelableExtra(EXTRA_URI);
            String usbDeviceName = intent.getStringExtra(EXTRA_USB_DEVICE_NAME);

            if (treeUri != null && !isRestoreRunning) {
                isRestoreRunning = true;
                restoredFilesCount = 0;
                failedFilesCount = 0;
                currentBytesRestored = 0;
                currentPercent = 0;

                startForeground(NOTIFICATION_ID, createNotification("מתחיל שחזור קבצים...", 0, "מעבד נתונים..."));

                // Initialize source matching the scan source
                try {
                    if (sourceUri != null) {
                        byteSource = new FileImageSource(getContentResolver(), sourceUri);
                        DiagnosticReporter.log("RestoreService: Using FileImageSource for URI: " + sourceUri);
                    } else if (usbDeviceName != null) {
                        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                        UsbDevice target = null;
                        for (UsbDevice d : deviceList.values()) {
                            if (d.getDeviceName().equals(usbDeviceName)) {
                                target = d;
                                break;
                            }
                        }
                        if (target != null) {
                            if (!usbManager.hasPermission(target)) {
                                DiagnosticReporter.log("RestoreService: FATAL - USB device lacks permission.");
                                throw new Exception("USB device permission denied");
                            }
                            byteSource = new UsbMassStorageSource(this, target);
                            DiagnosticReporter.log("RestoreService: Using UsbMassStorageSource for device: " + usbDeviceName);
                        } else {
                            throw new Exception("USB device not found: " + usbDeviceName);
                        }
                    } else {
                        throw new Exception("No scan source provided to RestoreService.");
                    }
                } catch (Exception e) {
                    DiagnosticReporter.logError(e, "RestoreService failed to open source");
                    List<String> errs = new ArrayList<>();
                    errs.add("שגיאה בפתיחת מקור הקריאה לשחזור: " + e.getMessage());
                    finishRestore(0, 1, errs);
                    return START_NOT_STICKY;
                }

                startRestoreThread(treeUri, saveAll);
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel activeChannel = new NotificationChannel(
                        CHANNEL_ACTIVE_ID,
                        "SD Rescue Restore Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                activeChannel.setSound(null, null);
                manager.createNotificationChannel(activeChannel);

                NotificationChannel completedChannel = new NotificationChannel(
                        CHANNEL_COMPLETED_ID,
                        "SD Rescue Restore Results",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                manager.createNotificationChannel(completedChannel);
            }
        }
    }

    private Notification createNotification(String text, int progress, String subText) {
        Intent notificationIntent = new Intent(this, ScanActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, RestoreService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ACTIVE_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("משחזר קבצים")
                .setContentText(text)
                .setSubText(subText)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, false)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ביטול", cancelPendingIntent);

        return builder.build();
    }

    private void startRestoreThread(final Uri treeUri, final boolean saveAll) {
        restoreThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                List<String> errorLogs = new ArrayList<>();

                List<RecoveredEntry> entriesToRestore = saveAll ? dbHelper.getAllEntriesForSaveAll() : dbHelper.getAllSelectedFiles();
                totalFilesCount = entriesToRestore.size();
                totalBytesCount = 0;
                for (RecoveredEntry e : entriesToRestore) {
                    if (!e.isDirectory()) totalBytesCount += e.getLength();
                }

                DocumentFileCompat rootDir = DocumentFileCompat.fromTreeUri(RestoreService.this, treeUri);
                if (rootDir == null || !rootDir.canWrite()) {
                    errorLogs.add("תקיית היעד אינה נגישה או שאין הרשאת כתיבה.");
                    finishRestore(0, entriesToRestore.size(), errorLogs);
                    return;
                }

                long startTime = System.currentTimeMillis();
                long lastNotificationUpdate = 0;
                final int windowSize = 30;
                long[] timeHistory = new long[windowSize];
                long[] byteHistory = new long[windowSize];
                int historyIndex = 0;
                long bytesSinceLastHistory = 0;
                long lastHistoryTime = startTime;

                for (int i = 0; i < windowSize; i++) {
                    timeHistory[i] = startTime;
                    byteHistory[i] = 0;
                }

                for (int i = 0; i < entriesToRestore.size(); i++) {
                    if (isCancelled) break;
                    RecoveredEntry entry = entriesToRestore.get(i);
                    currentFileName = entry.getName();

                    try {
                        saveNodeToDestination(resolver, rootDir, entry, errorLogs);
                        if (!entry.isDirectory()) {
                            restoredFilesCount++;
                            currentBytesRestored += entry.getLength();
                            bytesSinceLastHistory += entry.getLength();
                        }
                    } catch (Exception e) {
                        failedFilesCount++;
                        String errInfo = "נכשל " + entry.getName() + " (Offset: 0x" + Long.toHexString(entry.getOffset()) + "): " + e.getMessage();
                        errorLogs.add(errInfo);
                        DiagnosticReporter.logError(e, "Restore file failed completely");
                    }

                    if (totalBytesCount > 0) {
                        currentPercent = (int) Math.min(100, (currentBytesRestored * 100) / totalBytesCount);
                    } else {
                        currentPercent = (int) (((i + 1) * 100) / Math.max(1, totalFilesCount));
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastHistoryTime >= 1000) {
                        timeHistory[historyIndex] = now;
                        byteHistory[historyIndex] = bytesSinceLastHistory;
                        historyIndex = (historyIndex + 1) % windowSize;
                        bytesSinceLastHistory = 0;
                        lastHistoryTime = now;
                    }

                    long oldestTime = timeHistory[historyIndex];
                    long bytesInWindow = 0;
                    for (long b : byteHistory) bytesInWindow += b;
                    long timeSpan = now - oldestTime;

                    double bytesPerSec = timeSpan > 500 ? (bytesInWindow * 1000.0) / timeSpan : 0;
                    double gbRestored = currentBytesRestored / (1024.0 * 1024.0 * 1024.0);
                    double gbTotal = totalBytesCount / (1024.0 * 1024.0 * 1024.0);

                    long remainingBytes = totalBytesCount - currentBytesRestored;
                    long remainingSec = bytesPerSec > 1024 ? (long) (remainingBytes / bytesPerSec) : 0;
                    long remainingMin = Math.max(1, remainingSec / 60);

                    currentEtaText = String.format(Locale.getDefault(), "קבצים %d מתוך %d · %.2f GB מתוך %.2f GB · נשארו כ-%d דקות",
                            (restoredFilesCount + failedFilesCount), totalFilesCount, gbRestored, gbTotal, remainingMin);

                    if (now - lastNotificationUpdate >= 500) {
                        lastNotificationUpdate = now;
                        updateNotification(currentFileName, currentPercent, currentEtaText);
                        broadcastProgress();
                    }
                }

                finishRestore(restoredFilesCount, failedFilesCount, errorLogs);
            }
        });
        restoreThread.start();
    }

    private void saveNodeToDestination(ContentResolver resolver, DocumentFileCompat rootDir, RecoveredEntry node, List<String> errorLogs) throws Exception {
        String path = node.getPath();
        DocumentFileCompat currentParent = rootDir;

        if (path != null && !path.equals("/")) {
            String[] segments = path.split("/");
            for (String seg : segments) {
                if (seg.isEmpty()) continue;
                DocumentFileCompat foundSub = currentParent.findFile(seg);
                if (foundSub != null) {
                    currentParent = foundSub;
                } else {
                    DocumentFileCompat createdSub = currentParent.createDirectory(seg);
                    if (createdSub != null) {
                        currentParent = createdSub;
                    } else {
                        throw new Exception("Failed to create directory: " + seg);
                    }
                }
            }
        }

        if (node.isDirectory()) {
            DocumentFileCompat foundDir = currentParent.findFile(node.getName());
            if (foundDir == null) {
                currentParent.createDirectory(node.getName());
            }
        } else {
            String sanitizedName = sanitizeFileName(node.getName());
            String mimeType = getMimeTypeForFile(sanitizedName, node.getType());

            String uniqueName = sanitizedName;
            int suffix = 1;
            while (currentParent.findFile(uniqueName) != null) {
                int dotIdx = sanitizedName.lastIndexOf('.');
                if (dotIdx > 0) {
                    uniqueName = sanitizedName.substring(0, dotIdx) + "_" + suffix + sanitizedName.substring(dotIdx);
                } else {
                    uniqueName = sanitizedName + "_" + suffix;
                }
                suffix++;
            }

            DocumentFileCompat targetFile = currentParent.createFile(mimeType, uniqueName);
            if (targetFile == null) {
                throw new Exception("Failed to create document file: " + uniqueName);
            }

            if (byteSource == null) {
                throw new Exception("ByteSource is null during restore.");
            }

            try (OutputStream out = resolver.openOutputStream(targetFile.getUri())) {
                if (out == null) {
                    throw new Exception("Could not open OutputStream for target URI.");
                }
                long remaining = node.getLength();
                long offset = node.getOffset();
                byte[] buf = new byte[262144]; // 256 KB chunks

                while (remaining > 0 && !isCancelled) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int read = byteSource.readAt(offset, buf, toRead);
                    if (read <= 0) {
                        throw new Exception("Read failed or reached EOF prematurely at offset 0x" + Long.toHexString(offset));
                    }
                    out.write(buf, 0, read);
                    offset += read;
                    remaining -= read;
                }
                out.flush();
            }
        }
    }

    private String getMimeTypeForFile(String fileName, FileType type) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return FileType.safeMimeType(type);
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[/\\\\?%*:|<>\"]", "_");
    }

    private void updateNotification(String text, int progress, String subText) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text, progress, subText));
        }
    }

    private void broadcastProgress() {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra("restored", restoredFilesCount);
        intent.putExtra("failed", failedFilesCount);
        intent.putExtra("total", totalFilesCount);
        intent.putExtra("percent", currentPercent);
        intent.putExtra("eta", currentEtaText);
        sendBroadcast(intent);
    }

    private void finishRestore(int success, int failed, List<String> errors) {
        isRestoreRunning = false;

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);

            Intent reportIntent = new Intent(this, ScanActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, reportIntent, PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_COMPLETED_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            String summaryMsg = String.format(Locale.getDefault(), "השחזור הסתיים: %d שחזור מוצלח, %d נכשלו", success, failed);
            builder.setContentTitle("SD Rescue - שחזור הושלם")
                    .setContentText(summaryMsg)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            manager.notify(NOTIFICATION_ID + 1, builder.build());
        }

        Intent intent = new Intent(ACTION_COMPLETED);
        intent.putExtra("success", success);
        intent.putExtra("failed", failed);
        sendBroadcast(intent);

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCancelled = true;
        isRestoreRunning = false;
        if (restoreThread != null) {
            restoreThread.interrupt();
        }
        if (byteSource != null) {
            byteSource.close();
            byteSource = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }
}
