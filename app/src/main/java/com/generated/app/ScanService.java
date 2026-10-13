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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ScanService extends Service {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    public static final String ACTION_PROGRESS = "com.generated.app.ACTION_PROGRESS";
    public static final String ACTION_FILE_FOUND = "com.generated.app.ACTION_FILE_FOUND";
    public static final String ACTION_COMPLETED = "com.generated.app.ACTION_COMPLETED";
    public static final String ACTION_PAUSE = "com.generated.app.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.generated.app.ACTION_RESUME";
    public static final String ACTION_CANCEL = "com.generated.app.ACTION_CANCEL";

    private static final String CHANNEL_ID = "scan_service_channel";
    private static final int NOTIFICATION_ID = 101;

    private ByteSource byteSource;
    private Carver carver;
    private Thread scanThread;
    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;
    private final List<RecoveredEntry> foundEntries = new ArrayList<>();
    private final List<String> errorLog = new ArrayList<>();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
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
                updateNotification("סורק קבצים...", 0);
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

            try {
                if (uri != null) {
                    byteSource = new FileImageSource(getContentResolver(), uri);
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
                    }
                }
            } catch (Exception e) {
                stopSelf();
                return START_NOT_STICKY;
            }

            if (byteSource != null) {
                carver = new Carver();
                startForeground(NOTIFICATION_ID, createNotification("סורק קבצים...", 0));
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
                    List<RecoveredEntry> fsEntries = FilesystemParser.parseFilesystem(byteSource);
                    for (RecoveredEntry entry : fsEntries) {
                        while (isPaused && !isCancelled) {
                            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                        }
                        if (isCancelled) return;
                        foundEntries.add(entry);
                        broadcastFileFound(entry);
                    }

                    carver.carve(byteSource, new CarverCallback() {
                        @Override
                        public void onFileFound(CarvedFile carvedFile) {
                            while (isPaused && !isCancelled) {
                                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                            }
                            if (isCancelled) return;

                            boolean exists = false;
                            for (RecoveredEntry h : foundEntries) {
                                if (Math.abs(h.getOffset() - carvedFile.getOffset()) < 4096) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                RecoveredEntry entry = new RecoveredEntry(
                                        "carved_" + carvedFile.getOffset() + "." + carvedFile.getType().getName().toLowerCase(),
                                        "/carved",
                                        carvedFile.getOffset(),
                                        carvedFile.getLength(),
                                        carvedFile.getType(),
                                        true,
                                        false
                                );
                                foundEntries.add(entry);
                                broadcastFileFound(entry);
                            }
                        }

                        @Override
                        public void onProgress(long bytesScanned, long totalBytes) {
                            while (isPaused && !isCancelled) {
                                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                            }
                            if (isCancelled) return;

                            int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                            updateNotification("סרוק: " + percent + "% • נמצאו: " + foundEntries.size(), percent);
                            broadcastProgress(percent, foundEntries.size());
                        }
                    });

                } catch (Exception e) {
                    errorLog.add("שגיאת סריקה: " + e.getMessage());
                } finally {
                    broadcastCompleted();
                    if (byteSource != null) {
                        byteSource.close();
                    }
                    stopSelf();
                }
            }
        });
        scanThread.start();
    }

    private void broadcastFileFound(RecoveredEntry entry) {
        Intent intent = new Intent(ACTION_FILE_FOUND);
        intent.putExtra("name", entry.getName());
        intent.putExtra("path", entry.getPath());
        intent.putExtra("offset", entry.getOffset());
        intent.putExtra("length", entry.getLength());
        intent.putExtra("type", entry.getType().name());
        intent.putExtra("is_deleted", entry.isDeleted());
        sendBroadcast(intent);
    }

    private void broadcastProgress(int percent, int count) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra("percent", percent);
        intent.putExtra("count", count);
        sendBroadcast(intent);
    }

    private void broadcastCompleted() {
        Intent intent = new Intent(ACTION_COMPLETED);
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
