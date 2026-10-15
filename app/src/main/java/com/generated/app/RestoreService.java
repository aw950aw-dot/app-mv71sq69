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
import android.os.IBinder;
import android.os.PowerManager;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RestoreService extends Service {

    public static final String EXTRA_TREE_URI = "extra_tree_uri";
    public static final String EXTRA_SAVE_ALL = "extra_save_all";
    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    public static final String EXTRA_RESUME = "extra_resume";

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
    private static int skippedExistingFilesCount = 0;
    private static int foldersCreatedCount = 0;
    private static int foldersReusedCount = 0;
    private static int totalFilesCount = 0;
    private static long totalBytesCount = 0;
    private static long currentBytesRestored = 0;
    private static String currentFileName = "";
    private static int currentPercent = 0;
    private static String currentEtaText = "מחשב זמן נותר...";
    private static final Map<String, Integer> failureCausesMap = new HashMap<>();

    public static boolean isRestoreRunning() {
        return isRestoreRunning;
    }

    public static int getRestoredFilesCount() { return restoredFilesCount; }
    public static int getFailedFilesCount() { return failedFilesCount; }
    public static int getSkippedExistingFilesCount() { return skippedExistingFilesCount; }
    public static int getFoldersCreatedCount() { return foldersCreatedCount; }
    public static int getFoldersReusedCount() { return foldersReusedCount; }
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
            wakeLock.acquire(4 * 60 * 60 * 1000L);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CANCEL.equals(action)) {
                isCancelled = true;
                isRestoreRunning = false;
                dbHelper.updateJobState("restore", "CANCELLED", System.currentTimeMillis(), System.currentTimeMillis(), 0, restoredFilesCount, currentBytesRestored);
                ServiceState.getInstance().update(ServiceState.Phase.CANCELLED, restoredFilesCount, 0, currentBytesRestored, currentFileName, currentPercent, currentEtaText, "שחזור בוטל");
                if (restoreThread != null) restoreThread.interrupt();
                stopSelf();
                return START_NOT_STICKY;
            }

            Uri treeUri = intent.getParcelableExtra(EXTRA_TREE_URI);
            boolean saveAll = intent.getBooleanExtra(EXTRA_SAVE_ALL, true);
            boolean isResume = intent.getBooleanExtra(EXTRA_RESUME, false);
            Uri sourceUri = intent.getParcelableExtra(EXTRA_URI);
            String usbDeviceName = intent.getStringExtra(EXTRA_USB_DEVICE_NAME);

            if (treeUri != null && !isRestoreRunning) {
                isRestoreRunning = true;
                if (!isResume) {
                    restoredFilesCount = 0;
                    failedFilesCount = 0;
                    skippedExistingFilesCount = 0;
                    foldersCreatedCount = 0;
                    foldersReusedCount = 0;
                    currentBytesRestored = 0;
                    currentPercent = 0;
                    failureCausesMap.clear();
                    dbHelper.clearRestoreQueue();
                } else {
                    int[] stats = dbHelper.getRestoreQueueStats();
                    restoredFilesCount = stats[0];
                }

                dbHelper.updateJobState("restore", "RUNNING", System.currentTimeMillis(), System.currentTimeMillis(), 0, restoredFilesCount, currentBytesRestored);
                ServiceState.getInstance().update(ServiceState.Phase.RESTORING, restoredFilesCount, 0, currentBytesRestored, "", 0, "מתחיל שחזור...", "");

                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(4 * 60 * 60 * 1000L);
                }

                startForeground(NOTIFICATION_ID, createNotification("מתחיל שחזור קבצים...", 0, "מעבד נתונים..."));

                try {
                    if (sourceUri != null) {
                        byteSource = new FileImageSource(getContentResolver(), sourceUri);
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
                                throw new Exception("USB device permission denied");
                            }
                            byteSource = new UsbMassStorageSource(this, target);
                        } else {
                            throw new Exception("USB device not found: " + usbDeviceName);
                        }
                    } else {
                        throw new Exception("No scan source provided to RestoreService.");
                    }
                } catch (Exception e) {
                    DiagnosticReporter.logError(e, "RestoreService failed to open source");
                    String errMsg = "שגיאה בפתיחת מקור הקריאה לשחזור: " + e.getMessage();
                    isRestoreRunning = false;
                    dbHelper.updateJobState("restore", "INTERRUPTED", System.currentTimeMillis(), System.currentTimeMillis(), 0, restoredFilesCount, currentBytesRestored);
                    updateNotification(errMsg, 0, "שחזור נכשל");
                    ServiceState.getInstance().update(ServiceState.Phase.IDLE, restoredFilesCount, 0, currentBytesRestored, "", 0, "", errMsg);
                    List<String> errs = new ArrayList<>();
                    errs.add(errMsg);
                    finishRestore(0, 1, 0, errs);
                    return START_NOT_STICKY;
                }

                startRestoreThread(treeUri, saveAll, isResume);
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

    private void startRestoreThread(final Uri treeUri, final boolean saveAll, final boolean isResume) {
        restoreThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                List<String> errorLogs = new ArrayList<>();

                DocumentFileCompat rootDir = DocumentFileCompat.fromTreeUri(RestoreService.this, treeUri);
                if (rootDir == null || !rootDir.canWrite()) {
                    errorLogs.add("תקיית היעד אינה נגישה או שאין הרשאת כתיבה.");
                    finishRestore(0, 0, 0, errorLogs);
                    return;
                }

                List<RecoveredEntry> allEntries = dbHelper.getAllEntriesForSaveAll();
                Map<Long, RecoveredEntry> entryMap = new HashMap<>();
                for (RecoveredEntry e : allEntries) {
                    entryMap.put(e.getId(), e);
                }

                List<RecoveredEntry> topLevelItems = new ArrayList<>();
                for (RecoveredEntry e : allEntries) {
                    boolean isSelected = saveAll || e.isSelected() || e.isPartial();
                    if (!isSelected) continue;

                    boolean hasSelectedAncestor = false;
                    long currParentId = e.getParentId();
                    while (currParentId != 0 && entryMap.containsKey(currParentId)) {
                        RecoveredEntry parentEntry = entryMap.get(currParentId);
                        if (parentEntry != null && (saveAll || parentEntry.isSelected() || parentEntry.isPartial())) {
                            hasSelectedAncestor = true;
                            break;
                        }
                        currParentId = parentEntry != null ? parentEntry.getParentId() : 0;
                    }

                    if (!hasSelectedAncestor) {
                        topLevelItems.add(e);
                    }
                }

                HashMap<Long, DocumentFileCompat> folderMap = new HashMap<>();
                List<RecoveredEntry> orderedFolders = dbHelper.getOrderedFoldersForRestore(saveAll);
                
                Set<Long> validTopLevelIds = new HashSet<>();
                for (RecoveredEntry tl : topLevelItems) {
                    validTopLevelIds.add(tl.getId());
                }

                Set<Long> allowedNodeIds = new HashSet<>();
                for (RecoveredEntry tl : topLevelItems) {
                    allowedNodeIds.add(tl.getId());
                    if (tl.isDirectory()) {
                        addSubtreeIds(tl.getId(), entryMap, allowedNodeIds);
                    }
                }

                for (RecoveredEntry folderEntry : orderedFolders) {
                    if (isCancelled) break;
                    if (!allowedNodeIds.contains(folderEntry.getId())) continue;

                    try {
                        if (validTopLevelIds.contains(folderEntry.getId())) {
                            String folderName = sanitizeFileName(folderEntry.getName());
                            DocumentFileCompat existing = rootDir.findFile(folderName);
                            if (existing != null && existing.canWrite()) {
                                foldersReusedCount++;
                                folderMap.put(folderEntry.getId(), existing);
                            } else {
                                DocumentFileCompat created = null;
                                try {
                                    created = rootDir.createDirectory(folderName);
                                } catch (Exception ignored) {}

                                if (created == null) {
                                    try { Thread.sleep(300); } catch (Exception ignored2) {}
                                    created = rootDir.createDirectory(folderName);
                                }

                                if (created == null) {
                                    existing = rootDir.findFile(folderName);
                                    if (existing != null) {
                                        foldersReusedCount++;
                                        folderMap.put(folderEntry.getId(), existing);
                                    } else {
                                        throw new Exception("Failed to create top-level directory: " + folderName);
                                    }
                                } else {
                                    foldersCreatedCount++;
                                    folderMap.put(folderEntry.getId(), created);
                                }
                            }
                        } else {
                            long parentId = folderEntry.getParentId();
                            DocumentFileCompat parentDoc = folderMap.get(parentId);
                            if (parentDoc == null) {
                                parentDoc = rootDir;
                            }

                            String folderName = sanitizeFileName(folderEntry.getName());
                            DocumentFileCompat existing = parentDoc.findFile(folderName);
                            if (existing != null && existing.canWrite()) {
                                foldersReusedCount++;
                                folderMap.put(folderEntry.getId(), existing);
                            } else {
                                DocumentFileCompat created = null;
                                try {
                                    created = parentDoc.createDirectory(folderName);
                                } catch (Exception ignored) {}

                                if (created == null) {
                                    try { Thread.sleep(300); } catch (Exception ignored2) {}
                                    created = parentDoc.createDirectory(folderName);
                                }

                                if (created == null) {
                                    existing = parentDoc.findFile(folderName);
                                    if (existing != null) {
                                        foldersReusedCount++;
                                        folderMap.put(folderEntry.getId(), existing);
                                    } else {
                                        throw new Exception("Failed to create directory: " + folderName);
                                    }
                                } else {
                                    foldersCreatedCount++;
                                    folderMap.put(folderEntry.getId(), created);
                                }
                            }
                        }
                    } catch (Exception e) {
                        String errInfo = "נכשלה יצירת תיקייה " + folderEntry.getName() + ": " + e.getMessage();
                        errorLogs.add(errInfo);
                        recordFailureCause(e.getMessage());
                        DiagnosticReporter.logError(e, "Folder creation failed");
                    }
                }

                List<RecoveredEntry> allFiles = dbHelper.getFilesForRestore(saveAll);
                List<RecoveredEntry> filesToRestore = new ArrayList<>();
                for (RecoveredEntry fileEntry : allFiles) {
                    boolean shouldRestore = false;
                    if (saveAll) {
                        shouldRestore = true;
                    } else if (fileEntry.isSelected()) {
                        shouldRestore = true;
                    } else {
                        long currParent = fileEntry.getParentId();
                        while (currParent != 0 && entryMap.containsKey(currParent)) {
                            if (allowedNodeIds.contains(currParent)) {
                                shouldRestore = true;
                                break;
                            }
                            currParent = entryMap.get(currParent).getParentId();
                        }
                    }

                    if (shouldRestore) {
                        if (isResume && fileEntry.getRestoreStatus() == 1) {
                            continue;
                        }
                        filesToRestore.add(fileEntry);
                    }
                }

                totalFilesCount = allFiles.size();
                totalBytesCount = 0;
                for (RecoveredEntry e : allFiles) {
                    if (saveAll || e.isSelected() || allowedNodeIds.contains(e.getParentId())) {
                        totalBytesCount += e.getLength();
                    }
                }

                if (isResume) {
                    currentBytesRestored = 0;
                    for (RecoveredEntry e : allFiles) {
                        if (e.getRestoreStatus() == 1) {
                            currentBytesRestored += e.getLength();
                        }
                    }
                }

                long startTime = System.currentTimeMillis();
                long lastNotificationUpdate = 0;
                long lastHeartbeatUpdate = 0;
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

                for (int i = 0; i < filesToRestore.size(); i++) {
                    if (isCancelled) break;
                    RecoveredEntry entry = filesToRestore.get(i);
                    currentFileName = entry.getName();

                    try {
                        DocumentFileCompat parentDoc = rootDir;
                        long parentId = entry.getParentId();
                        if (folderMap.containsKey(parentId)) {
                            parentDoc = folderMap.get(parentId);
                        } else {
                            parentDoc = rootDir;
                        }

                        boolean skipped = saveFileToParent(resolver, parentDoc, entry, errorLogs);
                        if (skipped) {
                            skippedExistingFilesCount++;
                        } else {
                            restoredFilesCount++;
                            currentBytesRestored += entry.getLength();
                            bytesSinceLastHistory += entry.getLength();
                        }
                        dbHelper.updateRestoreStatus(entry.getId(), 1);
                    } catch (Exception e) {
                        failedFilesCount++;
                        dbHelper.updateRestoreStatus(entry.getId(), 2);
                        String errInfo = "נכשל " + entry.getName() + " (Offset: 0x" + Long.toHexString(entry.getOffset()) + "): " + e.getMessage();
                        errorLogs.add(errInfo);
                        recordFailureCause(e.getMessage());
                        DiagnosticReporter.logError(e, "Restore file failed completely");
                    }

                    if (totalBytesCount > 0) {
                        currentPercent = (int) Math.min(100, (currentBytesRestored * 100) / totalBytesCount);
                    } else {
                        currentPercent = (int) (((i + 1) * 100) / Math.max(1, filesToRestore.size()));
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeatUpdate >= 2000) {
                        lastHeartbeatUpdate = now;
                        dbHelper.updateJobState("restore", "RUNNING", startTime, now, foldersCreatedCount + foldersReusedCount, restoredFilesCount, currentBytesRestored);
                    }

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
                            (restoredFilesCount + skippedExistingFilesCount + failedFilesCount), totalFilesCount, gbRestored, gbTotal, remainingMin);

                    ServiceState.getInstance().update(ServiceState.Phase.RESTORING, restoredFilesCount, 0, currentBytesRestored, currentFileName, currentPercent, currentEtaText, "");

                    if (now - lastNotificationUpdate >= 500) {
                        lastNotificationUpdate = now;
                        updateNotification(currentFileName, currentPercent, currentEtaText);
                        broadcastProgress();
                    }
                }

                if (!isCancelled) {
                    dbHelper.clearRestoreQueue();
                }

                dbHelper.updateJobState("restore", "COMPLETED", startTime, System.currentTimeMillis(), foldersCreatedCount + foldersReusedCount, restoredFilesCount, currentBytesRestored);
                finishRestore(restoredFilesCount, failedFilesCount, skippedExistingFilesCount, errorLogs);
            }
        });
        restoreThread.start();
    }

    private void addSubtreeIds(long parentId, Map<Long, RecoveredEntry> entryMap, Set<Long> allowedNodeIds) {
        for (RecoveredEntry e : entryMap.values()) {
            if (e.getParentId() == parentId) {
                allowedNodeIds.add(e.getId());
                if (e.isDirectory()) {
                    addSubtreeIds(e.getId(), entryMap, allowedNodeIds);
                }
            }
        }
    }

    private void recordFailureCause(String message) {
        if (message == null) message = "Unknown error";
        int count = failureCausesMap.containsKey(message) ? failureCausesMap.get(message) : 0;
        failureCausesMap.put(message, count + 1);
    }

    private boolean saveFileToParent(ContentResolver resolver, DocumentFileCompat parentDoc, RecoveredEntry node, List<String> errorLogs) throws Exception {
        if (byteSource == null) {
            throw new Exception("ByteSource is null during restore.");
        }

        String sanitizedName = sanitizeFileName(node.getName());
        DocumentFileCompat existing = parentDoc.findFile(sanitizedName);
        if (existing != null) {
            try {
                long existingLen = existing.getLength();
                if (existingLen == node.getLength() && existingLen >= 0) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        byte[] firstChunk = new byte[(int) Math.min(node.getLength(), 4096L)];
        int initialRead = 0;
        if (node.getLength() > 0) {
            initialRead = byteSource.readAt(node.getOffset(), firstChunk, firstChunk.length);
            if (initialRead <= 0) {
                throw new Exception("Failed to read first chunk from source at offset 0x" + Long.toHexString(node.getOffset()));
            }
        }

        String mimeType = getMimeTypeForFile(sanitizedName, node.getType());

        DocumentFileCompat targetFile = parentDoc.createFile(mimeType, sanitizedName);
        if (targetFile == null) {
            throw new Exception("Failed to create document file: " + sanitizedName);
        }

        boolean writeSuccess = false;
        try (OutputStream out = resolver.openOutputStream(targetFile.getUri())) {
            if (out == null) {
                throw new Exception("Could not open OutputStream for target URI.");
            }

            long totalBytesWritten = 0;
            long expectedSize = node.getLength();

            if (expectedSize > 0) {
                out.write(firstChunk, 0, initialRead);
                totalBytesWritten += initialRead;

                long currentOffset = node.getOffset() + initialRead;
                long remaining = expectedSize - initialRead;

                byte[] buf = new byte[16384];
                while (remaining > 0 && !isCancelled) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int read = byteSource.readAt(currentOffset, buf, toRead);
                    if (read <= 0) {
                        throw new Exception("Chain shorter than size or read reached EOF prematurely at offset 0x" + Long.toHexString(currentOffset));
                    }
                    out.write(buf, 0, read);
                    currentOffset += read;
                    remaining -= read;
                    totalBytesWritten += read;
                }
            }
            out.flush();

            if (totalBytesWritten != expectedSize) {
                throw new Exception("Written bytes (" + totalBytesWritten + ") do not match expected file size (" + expectedSize + ")");
            }

            writeSuccess = true;
        } catch (Exception e) {
            try {
                android.provider.DocumentsContract.deleteDocument(resolver, targetFile.getUri());
            } catch (Exception ignored) {}
            throw e;
        }

        if (!writeSuccess) {
            try {
                android.provider.DocumentsContract.deleteDocument(resolver, targetFile.getUri());
            } catch (Exception ignored) {}
            throw new Exception("File write failed validation.");
        }

        return false;
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
        intent.putExtra("skipped", skippedExistingFilesCount);
        intent.putExtra("failed", failedFilesCount);
        intent.putExtra("total", totalFilesCount);
        intent.putExtra("percent", currentPercent);
        intent.putExtra("eta", currentEtaText);
        sendBroadcast(intent);
    }

    private void finishRestore(int success, int failed, int skipped, List<String> errors) {
        isRestoreRunning = false;

        if (byteSource != null) {
            byteSource.close();
            byteSource = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }

        if (failed == 0 && success > 0) {
            dbHelper.setAllSelected(false);
        }

        ServiceState.getInstance().update(ServiceState.Phase.RESTORE_COMPLETED, success, 0, currentBytesRestored, "", 100, "", "");

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

            String summaryMsg = String.format(Locale.getDefault(), "השחזור הסתיים: %d שוחזרו, %d דולגו, %d נכשלו", success, skipped, failed);
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
        intent.putExtra("skipped", skipped);
        intent.putExtra("folders_created", foldersCreatedCount);
        intent.putExtra("folders_reused", foldersReusedCount);
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
