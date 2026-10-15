package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 1001;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1002;

    private TextView tvFileName;
    private TextView tvFileSize;
    private Button btnStartScan;
    private ByteSource currentByteSource;
    private Uri currentUri;
    private UsbDevice currentUsbDevice;

    private final BroadcastReceiver detachReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice detachedDevice = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    detachedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
                } else {
                    detachedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                }

                if (detachedDevice != null && currentUsbDevice != null && detachedDevice.getDeviceId() == currentUsbDevice.getDeviceId()) {
                    if (ScanService.isScanRunning()) {
                        Intent stopScan = new Intent(MainActivity.this, ScanService.class);
                        stopScan.setAction(ScanService.ACTION_CANCEL);
                        startService(stopScan);
                    }
                    if (RestoreService.isRestoreRunning()) {
                        Intent stopRestore = new Intent(MainActivity.this, RestoreService.class);
                        stopRestore.setAction(RestoreService.ACTION_CANCEL);
                        startService(stopRestore);
                    }

                    if (currentByteSource != null) {
                        currentByteSource.close();
                        currentByteSource = null;
                    }
                    currentUsbDevice = null;
                    tvFileName.setText(getString(R.string.file_name_label) + getString(R.string.no_file_selected));
                    tvFileSize.setText(getString(R.string.file_size_label) + "-");
                    btnStartScan.setEnabled(false);

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("התקן נותק")
                            .setMessage("התקן ה-USB / קורא הכרטיסים נותק. החיבור נסגר ושמירת הנתונים הקיימת נשמרה.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    String stackTrace = sw.toString();

                    File crashFile = new File(getFilesDir(), "last_crash.txt");
                    java.io.FileWriter writer = new java.io.FileWriter(crashFile);
                    writer.write(stackTrace);
                    writer.close();
                } catch (Exception ignored) {}

                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });

        checkAndShowCrashDialog();
        requestNotificationPermissionIfNeeded();

        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        rootContainer.setBackgroundColor(0xFFF5F5F5);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        rootLayout.setPadding(padding, padding, padding, padding);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootContainer.addView(rootLayout, layoutParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_name);
        tvTitle.setTextSize(28);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        rootLayout.addView(tvTitle, titleParams);

        Button btnSelect = new Button(this);
        btnSelect.setText(R.string.select_image_file);
        btnSelect.setTextColor(0xFFFFFFFF);
        btnSelect.setBackgroundColor(0xFF1976D2);
        int btnPadding = (int) (14 * getResources().getDisplayMetrics().density);
        btnSelect.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        rootLayout.addView(btnSelect, btnParams);

        Button btnSelectUsb = new Button(this);
        btnSelectUsb.setText(R.string.select_usb_device);
        btnSelectUsb.setTextColor(0xFFFFFFFF);
        btnSelectUsb.setBackgroundColor(0xFF00838F);
        btnSelectUsb.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        rootLayout.addView(btnSelectUsb, btnParams);

        Button btnGuide = new Button(this);
        btnGuide.setText(R.string.menu_guide);
        btnGuide.setTextColor(0xFFFFFFFF);
        btnGuide.setBackgroundColor(0xFF6A1B9A);
        btnGuide.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        rootLayout.addView(btnGuide, btnParams);

        Button btnSettings = new Button(this);
        btnSettings.setText(R.string.menu_settings);
        btnSettings.setTextColor(0xFFFFFFFF);
        btnSettings.setBackgroundColor(0xFF455A64);
        btnSettings.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        rootLayout.addView(btnSettings, btnParams);

        btnStartScan = new Button(this);
        btnStartScan.setText(R.string.start_scan);
        btnStartScan.setTextColor(0xFFFFFFFF);
        btnStartScan.setBackgroundColor(0xFF388E3C);
        btnStartScan.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        btnStartScan.setEnabled(false);
        
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        scanParams.bottomMargin = (int) (20 * getResources().getDisplayMetrics().density);
        rootLayout.addView(btnStartScan, scanParams);

        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        cardLayout.setBackgroundColor(0xFFFFFFFF);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rootLayout.addView(cardLayout, cardParams);

        tvFileName = new TextView(this);
        tvFileName.setText(getString(R.string.file_name_label) + getString(R.string.no_file_selected));
        tvFileName.setTextSize(15);
        tvFileName.setTextColor(0xFF333333);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        cardLayout.addView(tvFileName, textParams);

        tvFileSize = new TextView(this);
        tvFileSize.setText(getString(R.string.file_size_label) + "-");
        tvFileSize.setTextSize(15);
        tvFileSize.setTextColor(0xFF333333);
        cardLayout.addView(tvFileSize, textParams);

        TextView tvVersion = new TextView(this);
        String buildDateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        tvVersion.setText("v0.5 build " + buildDateTime);
        tvVersion.setTextSize(11);
        tvVersion.setTextColor(0xFF888888);
        FrameLayout.LayoutParams versionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        versionParams.gravity = Gravity.BOTTOM | Gravity.END;
        int cornerMargin = (int) (12 * getResources().getDisplayMetrics().density);
        versionParams.setMargins(cornerMargin, cornerMargin, cornerMargin, cornerMargin);
        rootContainer.addView(tvVersion, versionParams);

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector();
            }
        });

        btnSelectUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUsbDeviceSelector();
            }
        });

        btnGuide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        btnStartScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUri != null) {
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    intent.putExtra(ScanActivity.EXTRA_URI, currentUri);
                    startActivity(intent);
                } else if (currentUsbDevice != null) {
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    intent.putExtra(ScanActivity.EXTRA_USB_DEVICE_NAME, currentUsbDevice.getDeviceName());
                    startActivity(intent);
                }
            }
        });

        setContentView(rootContainer);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            } else {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            }

            if (device != null) {
                loadUsbDevice(device);
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }

    private void checkAndShowCrashDialog() {
        File crashFile = new File(getFilesDir(), "last_crash.txt");
        if (crashFile.exists()) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(crashFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (Exception ignored) {}

            crashFile.delete();

            if (sb.length() > 0) {
                TextView tv = new TextView(this);
                tv.setText(sb.toString());
                tv.setTextSize(12);
                tv.setTextColor(0xFF333333);
                tv.setTextIsSelectable(true);
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad, pad, pad);

                android.widget.ScrollView sv = new android.widget.ScrollView(this);
                sv.addView(tv);

                new AlertDialog.Builder(this)
                        .setTitle("דוח קריסה מקודם (Crash Log)")
                        .setView(sv)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/octet-stream", "application/x-image", "image/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        try {
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void openUsbDeviceSelector() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        final List<UsbDevice> validDevices = new ArrayList<>();
        for (UsbDevice device : deviceList.values()) {
            boolean isMassStorage = false;
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                android.hardware.usb.UsbInterface intf = device.getInterface(i);
                if (intf.getInterfaceClass() == 0x08) {
                    isMassStorage = true;
                    break;
                }
            }
            if (isMassStorage || device.getInterfaceCount() > 0) {
                validDevices.add(device);
            }
        }

        if (validDevices.isEmpty()) {
            Toast.makeText(this, R.string.no_usb_device_found, Toast.LENGTH_LONG).show();
            return;
        }

        final String[] deviceNames = new String[validDevices.size()];
        for (int i = 0; i < validDevices.size(); i++) {
            UsbDevice d = validDevices.get(i);
            deviceNames[i] = (d.getProductName() != null ? d.getProductName() : "USB Device") + " (ID: " + d.getDeviceId() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_usb_device)
                .setItems(deviceNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadUsbDevice(validDevices.get(which));
                    }
                })
                .show();
    }

    private void loadUsbDevice(UsbDevice device) {
        if (currentByteSource != null) {
            currentByteSource.close();
            currentByteSource = null;
        }

        try {
            currentUri = null;
            currentUsbDevice = device;
            
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (!usbManager.hasPermission(device)) {
                Intent broadcastIntent = new Intent("com.generated.app.USB_PERMISSION");
                broadcastIntent.setPackage(getPackageName());
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(this, 0, broadcastIntent, flags);
                usbManager.requestPermission(device, pi);
            }

            currentByteSource = new UsbMassStorageSource(this, device);
            
            String displayName = device.getProductName() != null ? device.getProductName() : "USB Storage";
            long size = currentByteSource.getSize();

            tvFileName.setText(getString(R.string.file_name_label) + displayName);
            tvFileSize.setText(getString(R.string.file_size_label) + formatSize(size));
            btnStartScan.setEnabled(true);

            Toast.makeText(this, "התקן זוהה בהצלחה: " + displayName, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            currentUsbDevice = null;
            List<String> logs = UsbMassStorageSource.getDiagnosticLogs();
            StringBuilder sb = new StringBuilder();
            sb.append("שגיאה בפתיחת התקן USB:\n").append(e.getMessage()).append("\n\nדוח אבחון:\n");
            for (String log : logs) {
                sb.append(log).append("\n");
            }

            TextView tvLog = new TextView(this);
            tvLog.setText(sb.toString());
            tvLog.setTextSize(12);
            tvLog.setTextColor(0xFF333333);
            tvLog.setTextIsSelectable(true);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            tvLog.setPadding(pad, pad, pad, pad);

            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(tvLog);

            new AlertDialog.Builder(this)
                    .setTitle("דוח אבחון USB")
                    .setView(sv)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();

            tvFileName.setText(getString(R.string.file_name_label) + getString(R.string.no_file_selected));
            tvFileSize.setText(getString(R.string.file_size_label) + "-");
            btnStartScan.setEnabled(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    loadFile(uri);
                }
            }
        }
    }

    private void loadFile(Uri uri) {
        if (currentByteSource != null) {
            currentByteSource.close();
            currentByteSource = null;
        }

        try {
            currentUri = uri;
            currentUsbDevice = null;
            currentByteSource = new FileImageSource(getContentResolver(), uri);
            
            String displayName = getOriginalFileName(uri);
            long size = currentByteSource.getSize();

            tvFileName.setText(getString(R.string.file_name_label) + displayName);
            tvFileSize.setText(getString(R.string.file_size_label) + formatSize(size));
            btnStartScan.setEnabled(true);

        } catch (Exception e) {
            currentUri = null;
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_LONG).show();
            tvFileName.setText(getString(R.string.file_name_label) + getString(R.string.no_file_selected));
            tvFileSize.setText(getString(R.string.file_size_label) + "-");
            btnStartScan.setEnabled(false);
        }
    }

    private String getOriginalFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "unknown_file";
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 בייט";
        final String[] units = new String[]{"בייט", "קילובייט", "מגהבייט", "ג׳יגהבייט", "טרהבייט"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format(java.util.Locale.getDefault(), "%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(detachReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(detachReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(detachReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentByteSource != null) {
            currentByteSource.close();
            currentByteSource = null;
        }
    }
}
