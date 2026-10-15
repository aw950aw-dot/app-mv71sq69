package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StrictMode;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1002;
    private static final String PREF_BATTERY_DIALOG_SHOWN = "pref_battery_dialog_shown";
    private static final String PREF_DEST_URI = "pref_dest_uri";
    private static final String PREF_LAST_SCAN_DATE = "pref_last_scan_date";
    private static final String PREF_HAS_SAVED_SCAN = "pref_has_saved_scan";

    private LinearLayout cardStatusContainer;
    private LinearLayout cardPreviousScanContainer;
    private TextView tvStatusTitle;
    private TextView tvStatusSubtitle;
    private Button btnScanCard;
    private Button btnOpenResults;
    private Button btnScanAgain;

    private ByteSource currentByteSource;
    private Uri currentUri;
    private UsbDevice currentUsbDevice;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
                    updateUIState();

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("התקן נותק")
                            .setMessage("התקן ה-USB / קורא הכרטיסים נותק. החיבור נסגר.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isDebug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebug) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build());
        }
        
        if (ScanService.isScanRunning() || RestoreService.isRestoreRunning()) {
            startActivity(new Intent(this, ScanActivity.class));
            finish();
            return;
        }

        checkSavedScanStateOffMainThread();

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

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(0xFFF8F9FA);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(padding, padding, padding, padding);
        topBar.setBackgroundColor(0xFFFFFFFF);
        rootLayout.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_name);
        tvTitle.setTextSize(22);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        topBar.addView(tvTitle, titleLp);

        Button btnHelp = new Button(this);
        btnHelp.setText("❓");
        btnHelp.setTextSize(16);
        btnHelp.setTextColor(0xFF1976D2);
        btnHelp.setBackgroundColor(0x00000000);
        btnHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            }
        });
        topBar.addView(btnHelp, new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density)
        ));

        Button btnSettings = new Button(this);
        btnSettings.setText("⚙️");
        btnSettings.setTextSize(16);
        btnSettings.setTextColor(0xFF1976D2);
        btnSettings.setBackgroundColor(0x00000000);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        topBar.addView(btnSettings, new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density)
        ));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(padding, padding, padding, padding);
        scrollView.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        cardStatusContainer = new LinearLayout(this);
        cardStatusContainer.setOrientation(LinearLayout.VERTICAL);
        cardStatusContainer.setPadding(padding, padding, padding, padding);
        cardStatusContainer.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.bottomMargin = padding;
        contentContainer.addView(cardStatusContainer, cardLp);

        tvStatusTitle = new TextView(this);
        tvStatusTitle.setText(R.string.connect_card_prompt);
        tvStatusTitle.setTextSize(18);
        tvStatusTitle.setTextColor(0xFF333333);
        tvStatusTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        stParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        cardStatusContainer.addView(tvStatusTitle, stParams);

        tvStatusSubtitle = new TextView(this);
        tvStatusSubtitle.setTextSize(14);
        tvStatusSubtitle.setTextColor(0xFF757575);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        cardStatusContainer.addView(tvStatusSubtitle, subParams);

        btnScanCard = new Button(this);
        btnScanCard.setText(R.string.scan_card_button);
        btnScanCard.setTextColor(0xFFFFFFFF);
        btnScanCard.setBackgroundColor(0xFF1976D2);
        btnScanCard.setVisibility(View.GONE);
        int btnPad = (int) (14 * getResources().getDisplayMetrics().density);
        btnScanCard.setPadding(btnPad, btnPad, btnPad, btnPad);
        cardStatusContainer.addView(btnScanCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnScanCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndShowBatteryOptimizationDialog(new Runnable() {
                    @Override
                    public void run() {
                        proceedWithScan();
                    }
                });
            }
        });

        cardPreviousScanContainer = new LinearLayout(this);
        cardPreviousScanContainer.setOrientation(LinearLayout.VERTICAL);
        cardPreviousScanContainer.setPadding(padding, padding, padding, padding);
        cardPreviousScanContainer.setBackgroundColor(0xFFFFFFFF);
        cardPreviousScanContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams prevCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        prevCardLp.bottomMargin = padding;
        contentContainer.addView(cardPreviousScanContainer, prevCardLp);

        TextView tvPrevTitle = new TextView(this);
        tvPrevTitle.setText(R.string.previous_scan_title);
        tvPrevTitle.setTextSize(16);
        tvPrevTitle.setTextColor(0xFF1976D2);
        tvPrevTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams ptParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        ptParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        cardPreviousScanContainer.addView(tvPrevTitle, ptParams);

        final TextView tvPrevDetails = new TextView(this);
        tvPrevDetails.setTextSize(14);
        tvPrevDetails.setTextColor(0xFF555555);
        LinearLayout.LayoutParams pdParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pdParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        cardPreviousScanContainer.addView(tvPrevDetails, pdParams);

        LinearLayout prevBtnLayout = new LinearLayout(this);
        prevBtnLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardPreviousScanContainer.addView(prevBtnLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnOpenResults = new Button(this);
        btnOpenResults.setText(R.string.open_results);
        btnOpenResults.setTextColor(0xFFFFFFFF);
        btnOpenResults.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams bOpenLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        bOpenLp.rightMargin = 8;
        prevBtnLayout.addView(btnOpenResults, bOpenLp);

        btnScanAgain = new Button(this);
        btnScanAgain.setText(R.string.scan_again);
        btnScanAgain.setTextColor(0xFF333333);
        btnScanAgain.setBackgroundColor(0xFFE0E0E0);
        prevBtnLayout.addView(btnScanAgain, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnOpenResults.setOnClickListener(new View.OnClickListener() {
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
                } else {
                    startActivity(new Intent(MainActivity.this, ScanActivity.class));
                }
            }
        });

        btnScanAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(() -> {
                    DatabaseHelper.getInstance(MainActivity.this).clearDatabase();
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putBoolean(PREF_HAS_SAVED_SCAN, false).apply();
                    mainHandler.post(() -> {
                        cardPreviousScanContainer.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "הנתונים הקודמים נוקו, מוכן לסריקה חדשה", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            }
        });

        setContentView(rootLayout);
        checkForConnectedUsbDevice();
    }

    private void checkSavedScanStateOffMainThread() {
        new Thread(() -> {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            int totalFiles = db.getTotalFilesCount();
            int totalFolders = db.getTotalFoldersCount();
            SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
            boolean hasSaved = prefs.getBoolean(PREF_HAS_SAVED_SCAN, false) || (totalFiles > 0 || totalFolders > 0);

            if (hasSaved) {
                String dateStr = prefs.getString(PREF_LAST_SCAN_DATE, "תאריך לא ידוע");
                String filesPlural = getResources().getQuantityString(R.plurals.files_count, totalFiles, totalFiles);
                String foldersPlural = getResources().getQuantityString(R.plurals.folders_count, totalFolders, totalFolders);
                final String detailsText = String.format(Locale.getDefault(), "תאריך: %s\nמצא: %s, %s", dateStr, filesPlural, foldersPlural);
                mainHandler.post(() -> {
                    if (cardPreviousScanContainer != null) {
                        // Find TextView inside cardPreviousScanContainer if needed, or update text directly
                        for (int i = 0; i < cardPreviousScanContainer.getChildCount(); i++) {
                            View child = cardPreviousScanContainer.getChildAt(i);
                            if (child instanceof TextView && child != cardPreviousScanContainer.getChildAt(0)) {
                                ((TextView) child).setText(detailsText);
                                break;
                            }
                        }
                        cardPreviousScanContainer.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    private void checkForConnectedUsbDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            loadUsbDevice(device);
            break;
        }
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
            updateUIState();
        } catch (Exception e) {
            currentUsbDevice = null;
            updateUIState();
        }
    }

    private void updateUIState() {
        if (currentUsbDevice != null && currentByteSource != null) {
            String name = currentUsbDevice.getProductName() != null ? currentUsbDevice.getProductName() : "USB Card Reader";
            long size = currentByteSource.getSize();
            tvStatusTitle.setText(name);
            tvStatusSubtitle.setText("גודל הכרטיס: " + formatSize(size));
            btnScanCard.setVisibility(View.VISIBLE);
        } else {
            tvStatusTitle.setText(R.string.connect_card_prompt);
            tvStatusSubtitle.setText("חבר קורא כרטיסים באמצעות USB OTG כדי להתחיל");
            btnScanCard.setVisibility(View.GONE);
        }
    }

    private void showUnfinishedRestoreDialog() {
        new Thread(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
            int[] stats = dbHelper.getRestoreQueueStats();
            int done = stats[0];
            int total = stats[1];
            mainHandler.post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("שחזור לא הושלם")
                        .setMessage("נמצא שחזור קודם שלא הושלם: " + done + " מתוך " + total + " קבצים בוצעו.")
                        .setPositiveButton("Resume", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkAndShowBatteryOptimizationDialog(new Runnable() {
                                    @Override
                                    public void run() {
                                        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                                        String destUriStr = prefs.getString(PREF_DEST_URI, null);
                                        if (destUriStr != null) {
                                            Uri treeUri = Uri.parse(destUriStr);
                                            Intent intent = new Intent(MainActivity.this, RestoreService.class);
                                            intent.putExtra(RestoreService.EXTRA_TREE_URI, treeUri);
                                            intent.putExtra(RestoreService.EXTRA_RESUME, true);
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                startForegroundService(intent);
                                            } else {
                                                startService(intent);
                                            }
                                            startActivity(new Intent(MainActivity.this, ScanActivity.class));
                                            finish();
                                        } else {
                                            Toast.makeText(MainActivity.this, "לא נמצאה תיקיית יעד לשחזור", Toast.LENGTH_SHORT).show();
                                            new Thread(() -> dbHelper.clearRestoreQueue()).start();
                                            recreate();
                                        }
                                    }
                                });
                            }
                        })
                        .setNegativeButton("Discard", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new Thread(() -> DatabaseHelper.getInstance(MainActivity.this).clearRestoreQueue()).start();
                                recreate();
                            }
                        })
                        .setCancelable(false)
                        .show();
            });
        }).start();
    }

    private void checkAndShowBatteryOptimizationDialog(final Runnable onProceed) {
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        boolean shown = prefs.getBoolean(PREF_BATTERY_DIALOG_SHOWN, false);

        boolean isExempt = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                isExempt = pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        }

        if (!shown && !isExempt) {
            prefs.edit().putBoolean(PREF_BATTERY_DIALOG_SHOWN, true).apply();

            new AlertDialog.Builder(this)
                    .setTitle("פעולה ברקע ובמצב שינה")
                    .setMessage("תהליך הסריקה והשחזור עשוי לקחת זמן רב ועליו להמשיך ברקע גם כאשר המסך כבוי.\n\nהאם ברצונך לפתוח את הגדרות הסוללה כדי לאפשר פעולה רציפה ברקע?")
                    .setPositiveButton("הגדרות סוללה", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    try {
                                        Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                        startActivity(intent);
                                    } catch (Exception ignored2) {}
                                }
                            }
                            onProceed.run();
                        }
                    })
                    .setNegativeButton("Not now", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onProceed.run();
                        }
                    })
                    .setCancelable(false)
                    .show();
        } else {
            onProceed.run();
        }
    }

    private void proceedWithScan() {
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        prefs.edit().putString(PREF_LAST_SCAN_DATE, currentDate).putBoolean(PREF_HAS_SAVED_SCAN, true).apply();

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
        checkForConnectedUsbDevice();

        new Thread(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
            if (dbHelper.hasUnfinishedRestore()) {
                mainHandler.post(this::showUnfinishedRestoreDialog);
            }
        }).start();
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
