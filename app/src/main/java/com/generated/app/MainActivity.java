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
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StrictMode;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private CardInfoPanel cardInfoPanel;
    private FrameLayout iconCircle;
    private ImageView ivStatusIcon;
    private ScanAnimationView connectAnim;
    private ProgressBar connectBar;
    private Button btnRetry;

    // connection state of the card reader (opening the reader can take many seconds, so it runs in the background)
    private int connectGeneration = 0;
    private boolean connecting = false;
    private String connectError = null;
    private long connectStartMs = 0;
    private Uri currentUri;
    private UsbDevice currentUsbDevice;

    private static boolean unfinishedRestoreDialogShownThisLaunch = false;

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
                    connectGeneration++;
                    connecting = false;
                    connectError = null;
                    mainHandler.removeCallbacks(connectTicker);
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
        
        new Thread(() -> {
            DatabaseHelper db = DatabaseHelper.getInstance(MainActivity.this);
            db.checkAndInterruptStaleJobs();
            String scanPhase = db.getJobPhase("scan");
            String restorePhase = db.getJobPhase("restore");
            if ("RUNNING".equalsIgnoreCase(scanPhase) || "RUNNING".equalsIgnoreCase(restorePhase) ||
                ScanService.isScanRunning() || RestoreService.isRestoreRunning()) {
                mainHandler.post(() -> {
                    startActivity(new Intent(MainActivity.this, ScanActivity.class));
                    finish();
                });
            }
        }).start();

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
        getWindow().setStatusBarColor(0xFF0D47A1);

        final android.widget.ScrollView rootLayout = new android.widget.ScrollView(this);
        rootLayout.setBackgroundColor(0xFFF4F7FB);
        rootLayout.setFillViewport(true);

        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(contentContainer, new android.widget.ScrollView.LayoutParams(
                android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                android.widget.ScrollView.LayoutParams.WRAP_CONTENT));

        // ---- header: gradient with the title and two small buttons
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(64));
        GradientDrawable headerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0D47A1, 0xFF1E88E5});
        headerBg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        header.setBackground(headerBg);
        contentContainer.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView btnSettings = makePill("הגדרות", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        topRow.addView(btnSettings);

        TextView btnHelp = makePill("מדריך", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GuideActivity.class));
            }
        });
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helpLp.leftMargin = dp(8);
        topRow.addView(btnHelp, helpLp);

        topRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_name);
        tvTitle.setTextSize(26);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topRow.addView(tvTitle);

        TextView tvTagline = new TextView(this);
        tvTagline.setText("הצלת קבצים מכרטיס שמבקש פירמוט");
        tvTagline.setTextSize(14);
        tvTagline.setTextColor(0xD9FFFFFF);
        tvTagline.setGravity(Gravity.RIGHT);
        tvTagline.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = dp(6);
        header.addView(tvTagline, tagLp);

        // ---- status card (overlaps the header)
        cardStatusContainer = makeCard();
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = dp(16);
        cardLp.rightMargin = dp(16);
        cardLp.topMargin = -dp(44);
        contentContainer.addView(cardStatusContainer, cardLp);

        iconCircle = new FrameLayout(this);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(0xFFE3F2FD);
        iconCircle.setBackground(circleBg);
        ivStatusIcon = new ImageView(this);
        ivStatusIcon.setImageResource(R.drawable.ic_sd_card);
        ivStatusIcon.setColorFilter(0xFF1976D2);
        iconCircle.addView(ivStatusIcon, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(68), dp(68));
        iconLp.gravity = Gravity.RIGHT;
        iconLp.bottomMargin = dp(12);
        cardStatusContainer.addView(iconCircle, iconLp);

        connectAnim = new ScanAnimationView(this);

        tvStatusTitle = new TextView(this);
        tvStatusTitle.setText(R.string.connect_card_prompt);
        tvStatusTitle.setTextSize(20);
        tvStatusTitle.setTextColor(0xFF1F2937);
        tvStatusTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatusTitle.setGravity(Gravity.RIGHT);
        tvStatusTitle.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stParams.bottomMargin = dp(6);
        cardStatusContainer.addView(tvStatusTitle, stParams);

        tvStatusSubtitle = new TextView(this);
        tvStatusSubtitle.setTextSize(14);
        tvStatusSubtitle.setTextColor(0xFF6B7280);
        tvStatusSubtitle.setGravity(Gravity.RIGHT);
        tvStatusSubtitle.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(12);
        cardStatusContainer.addView(tvStatusSubtitle, subParams);

        connectBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        connectBar.setIndeterminate(true);
        connectBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(5));
        barLp.bottomMargin = dp(8);
        cardStatusContainer.addView(connectBar, barLp);

        cardInfoPanel = new CardInfoPanel(this);
        cardStatusContainer.addView(cardInfoPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnScanCard = makeButton(getString(R.string.scan_card_button), true);
        btnScanCard.setVisibility(View.GONE);
        cardStatusContainer.addView(btnScanCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        btnRetry = makeButton("נסה שוב", true);
        btnRetry.setVisibility(View.GONE);
        cardStatusContainer.addView(btnRetry, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUsbDevice != null) {
                    loadUsbDevice(currentUsbDevice);
                } else {
                    checkForConnectedUsbDevice();
                }
            }
        });

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

        // ---- previous scan card
        cardPreviousScanContainer = makeCard();
        cardPreviousScanContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams prevCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prevCardLp.leftMargin = dp(16);
        prevCardLp.rightMargin = dp(16);
        prevCardLp.topMargin = dp(16);
        contentContainer.addView(cardPreviousScanContainer, prevCardLp);

        // NOTE: checkSavedScanStateOffMainThread() writes the details into the second TextView of this card
        TextView tvPrevTitle = new TextView(this);
        tvPrevTitle.setText(R.string.previous_scan_title);
        tvPrevTitle.setTextSize(17);
        tvPrevTitle.setTextColor(0xFF1976D2);
        tvPrevTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPrevTitle.setGravity(Gravity.RIGHT);
        tvPrevTitle.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams ptParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ptParams.bottomMargin = dp(6);
        cardPreviousScanContainer.addView(tvPrevTitle, ptParams);

        final TextView tvPrevDetails = new TextView(this);
        tvPrevDetails.setTextSize(14);
        tvPrevDetails.setTextColor(0xFF4B5563);
        tvPrevDetails.setGravity(Gravity.RIGHT);
        tvPrevDetails.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams pdParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pdParams.bottomMargin = dp(14);
        cardPreviousScanContainer.addView(tvPrevDetails, pdParams);

        LinearLayout prevBtnLayout = new LinearLayout(this);
        prevBtnLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardPreviousScanContainer.addView(prevBtnLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnScanAgain = makeButton(getString(R.string.scan_again), false);
        LinearLayout.LayoutParams bAgainLp = new LinearLayout.LayoutParams(0, dp(50), 1.0f);
        bAgainLp.rightMargin = dp(8);
        prevBtnLayout.addView(btnScanAgain, bAgainLp);

        btnOpenResults = makeButton(getString(R.string.open_results), true);
        prevBtnLayout.addView(btnOpenResults, new LinearLayout.LayoutParams(0, dp(50), 1.0f));

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

        // ---- tip
        TextView tvTip = new TextView(this);
        tvTip.setText("טיפ: כשאנדרואיד שואל איזו אפליקציה לפתוח בחיבור הקורא, בחר SD Rescue וסמן \"תמיד\".");
        tvTip.setTextSize(13);
        tvTip.setTextColor(0xFF6B7280);
        tvTip.setGravity(Gravity.RIGHT);
        tvTip.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tipLp.leftMargin = dp(24);
        tipLp.rightMargin = dp(24);
        tipLp.topMargin = dp(18);
        tipLp.bottomMargin = dp(24);
        contentContainer.addView(tvTip, tipLp);

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
            // already connected (or being opened) to this very reader: do not open it again on every onResume
            if (currentUsbDevice != null && currentUsbDevice.getDeviceId() == device.getDeviceId()
                    && (connecting || currentByteSource != null || connectError != null)) {
                updateUIState();
                return;
            }
            loadUsbDevice(device);
            return;
        }
        if (currentUsbDevice != null && !connecting) {
            connectGeneration++;
            if (currentByteSource != null) {
                currentByteSource.close();
                currentByteSource = null;
            }
            currentUsbDevice = null;
            connectError = null;
        }
        updateUIState();
    }

    private void loadUsbDevice(final UsbDevice device) {
        final int gen = ++connectGeneration;
        if (currentByteSource != null) {
            currentByteSource.close();
            currentByteSource = null;
        }
        currentUri = null;
        currentUsbDevice = device;
        connecting = true;
        connectError = null;
        connectStartMs = System.currentTimeMillis();

        // show "reading the card" immediately; opening the reader can take a long time (permission, slow cards)
        updateUIState();
        mainHandler.removeCallbacks(connectTicker);
        mainHandler.postDelayed(connectTicker, 1000);

        final Context appContext = getApplicationContext();
        new Thread(() -> {
            UsbMassStorageSource opened = null;
            String error = null;
            try {
                opened = new UsbMassStorageSource(appContext, device);
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            final UsbMassStorageSource result = opened;
            final String err = error;
            mainHandler.post(() -> {
                if (gen != connectGeneration || isFinishing() || isDestroyed()) {
                    if (result != null) result.close();
                    return;
                }
                connecting = false;
                mainHandler.removeCallbacks(connectTicker);
                if (result != null) {
                    currentByteSource = result;
                    connectError = null;
                } else {
                    connectError = err != null ? err : "unknown";
                }
                updateUIState();
            });
        }, "UsbOpen").start();
    }

    private final Runnable connectTicker = new Runnable() {
        @Override
        public void run() {
            if (!connecting) return;
            long sec = (System.currentTimeMillis() - connectStartMs) / 1000;
            if (tvStatusSubtitle != null) {
                String text = "קורא את הכרטיס... " + sec + " שניות";
                if (sec >= 10) text += "\nהכרטיס איטי להגיב, ממשיכים להמתין";
                tvStatusSubtitle.setText(text);
            }
            mainHandler.postDelayed(this, 1000);
        }
    };

    private void updateUIState() {
        if (tvStatusTitle == null) return;
        String name = (currentUsbDevice != null && currentUsbDevice.getProductName() != null)
                ? currentUsbDevice.getProductName() : "USB Card Reader";

        boolean noCard = currentUsbDevice == null;
        boolean isConnecting = !noCard && connecting;
        boolean isError = !noCard && !connecting && currentByteSource == null;
        boolean isReady = !noCard && !connecting && currentByteSource != null;

        setAnimationVisible(isConnecting);
        connectBar.setVisibility(isConnecting ? View.VISIBLE : View.GONE);
        iconCircle.setVisibility(isConnecting ? View.GONE : View.VISIBLE);
        btnRetry.setVisibility(isError ? View.VISIBLE : View.GONE);
        btnScanCard.setVisibility(isReady ? View.VISIBLE : View.GONE);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);

        if (noCard) {
            circle.setColor(0xFFE3F2FD);
            ivStatusIcon.setColorFilter(0xFF1976D2);
            tvStatusTitle.setText(R.string.connect_card_prompt);
            tvStatusSubtitle.setText("חבר קורא כרטיסים בכבל USB OTG, והאפליקציה תזהה אותו אוטומטית");
            if (cardInfoPanel != null) cardInfoPanel.clear();
        } else if (isConnecting) {
            tvStatusTitle.setText(name);
            long sec = (System.currentTimeMillis() - connectStartMs) / 1000;
            tvStatusSubtitle.setText(sec < 2 ? "מזהה את הכרטיס..." : "קורא את הכרטיס... " + sec + " שניות");
            if (cardInfoPanel != null) cardInfoPanel.clear();
        } else if (isError) {
            circle.setColor(0xFFFFF3E0);
            ivStatusIcon.setColorFilter(0xFFEF6C00);
            tvStatusTitle.setText("לא ניתן לקרוא את הכרטיס");
            String err = connectError != null ? connectError : "";
            String reason;
            if (err.toLowerCase(Locale.ROOT).contains("permission")) {
                reason = "לא אושרה הגישה להתקן. לחץ \"נסה שוב\" ואשר את החלון שנפתח.";
            } else {
                reason = "נסה לנתק ולחבר מחדש את הקורא, או לנסות כבל או קורא אחר.";
            }
            tvStatusSubtitle.setText(reason + (err.isEmpty() ? "" : "\n(" + err + ")"));
            if (cardInfoPanel != null) cardInfoPanel.clear();
        } else {
            circle.setColor(0xFFE8F5E9);
            ivStatusIcon.setColorFilter(0xFF2E7D32);
            long size = currentByteSource.getSize();
            tvStatusTitle.setText(name);
            tvStatusSubtitle.setText("הכרטיס זוהה ומוכן לסריקה");
            if (cardInfoPanel != null) cardInfoPanel.load(currentByteSource, name + ":" + size);
        }
        if (!isConnecting) iconCircle.setBackground(circle);
    }

    /** The animation view is attached only while it is needed, so its animator does not run in the background. */
    private void setAnimationVisible(boolean visible) {
        if (visible && connectAnim.getParent() == null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
            lp.bottomMargin = dp(8);
            cardStatusContainer.addView(connectAnim, 0, lp);
        } else if (!visible && connectAnim.getParent() != null) {
            cardStatusContainer.removeView(connectAnim);
        }
    }

    // ---- small UI helpers
    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(20));
        card.setBackground(bg);
        card.setElevation(dp(4));
        return card;
    }

    private Button makeButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (primary) {
            bg.setColor(0xFF1976D2);
            b.setTextColor(0xFFFFFFFF);
        } else {
            bg.setColor(0xFFFFFFFF);
            bg.setStroke(dp(1.5f), 0xFF1976D2);
            b.setTextColor(0xFF1976D2);
        }
        b.setBackground(bg);
        return b;
    }

    private TextView makePill(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(18));
        t.setBackground(bg);
        t.setOnClickListener(l);
        return t;
    }

    private void showUnfinishedRestoreDialog() {
        if (unfinishedRestoreDialogShownThisLaunch) {
            return;
        }
        if (ScanService.isScanRunning() || RestoreService.isRestoreRunning()) {
            return;
        }
        unfinishedRestoreDialogShownThisLaunch = true;

        new Thread(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
            int[] stats = dbHelper.getRestoreQueueStats();
            int done = stats[0];
            int total = stats[1];
            mainHandler.post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("שחזור לא הושלם")
                        .setMessage("נמצא שחזור קודם שלא הושלם: " + done + " מתוך " + total + " קבצים בוצעו.")
                        .setPositiveButton("המשך שחזור", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkAndShowBatteryOptimizationDialog(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (currentUsbDevice == null && currentUri == null) {
                                            Toast.makeText(MainActivity.this, "חבר את קורא הכרטיסים", Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                                        String destUriStr = prefs.getString(PREF_DEST_URI, null);
                                        if (destUriStr != null) {
                                            Uri treeUri = Uri.parse(destUriStr);
                                            Intent intent = new Intent(MainActivity.this, RestoreService.class);
                                            intent.putExtra(RestoreService.EXTRA_TREE_URI, treeUri);
                                            intent.putExtra(RestoreService.EXTRA_RESUME, true);
                                            if (currentUsbDevice != null) {
                                                intent.putExtra(RestoreService.EXTRA_USB_DEVICE_NAME, currentUsbDevice.getDeviceName());
                                            } else if (currentUri != null) {
                                                intent.putExtra(RestoreService.EXTRA_URI, currentUri);
                                            }

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                startForegroundService(intent);
                                            } else {
                                                startService(intent);
                                            }
                                            startActivity(new Intent(MainActivity.this, ScanActivity.class));
                                            finish();
                                        } else {
                                            Toast.makeText(MainActivity.this, "לא נמצאה תיקיית יעד לשחזור", Toast.LENGTH_SHORT).show();
                                            new Thread(() -> dbHelper.discardRestore()).start();
                                            recreate();
                                        }
                                    }
                                });
                            }
                        })
                        .setNegativeButton("בטל שחזור", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new Thread(() -> {
                                    DatabaseHelper.getInstance(MainActivity.this).discardRestore();
                                    mainHandler.post(() -> {
                                        updateUIState();
                                        checkSavedScanStateOffMainThread();
                                    });
                                }).start();
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
        if (cardInfoPanel != null) cardInfoPanel.cancel();
        try {
            unregisterReceiver(detachReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectGeneration++;
        mainHandler.removeCallbacks(connectTicker);
        if (currentByteSource != null) {
            currentByteSource.close();
            currentByteSource = null;
        }
    }
}
