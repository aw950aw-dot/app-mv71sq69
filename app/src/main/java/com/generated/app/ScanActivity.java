package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScanActivity extends Activity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    private static final int REQUEST_CODE_SAVE_DIR = 2002;
    private static final int REQUEST_CODE_SAVE_REPORT = 2003;
    private static final String PREF_DEST_URI = "pref_dest_uri";

    private Uri sourceUri;
    private String usbDeviceName;
    private DatabaseHelper dbHelper;

    private LinearLayout layoutScanScreen;
    private TextView tvScanStatus;
    private TextView tvScanLiveCounters;
    private ProgressBar scanCircularProgress;
    private Button btnCancelScan;
    private Button btnToggleTechDetails;
    private LinearLayout layoutTechDetailsContainer;
    private TextView tvTechDetailsContent;
    private Button btnCopyReportScan;
    private Button btnSaveReportScan;

    private LinearLayout layoutResultsDashboard;
    private LinearLayout topBarResults;
    private Button btnSearchToggle;
    private EditText etSearchDashboard;
    private Button btnMenuResults;
    private ScrollView dashboardScrollContainer;
    private LinearLayout categoriesGridContainer;
    private LinearLayout cardBrowseFolders;
    private ProgressBar dashboardLoadingProgress;

    private LinearLayout layoutFolderBrowser;
    private LinearLayout layoutBreadcrumb;
    private EditText etSearchBrowser;
    private Button btnSelectAllBrowser;
    private Button btnDeselectAllBrowser;
    private ListView listViewBrowser;
    private ProgressBar browserLoadingProgress;
    private LinearLayout bottomBarRestore;
    private TextView tvRestoreSelectionInfo;
    private Button btnExecuteRestore;

    private String currentDirPath = "/";
    private long currentParentId = 0;
    private List<RecoveredEntry> currentDirEntries = new ArrayList<>();
    private List<RecoveredEntry> searchResults = new ArrayList<>();
    private boolean isSearching = false;
    private TreeEntryAdapter browserAdapter;

    private int currentUiMode = 1;
    private long scanStartTime = 0;
    private int lastBroadcastFiles = 0;
    private int lastBroadcastFolders = 0;

    private boolean saveAllModeFlag = false;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();

                if (ScanService.ACTION_PROGRESS.equals(action)) {
                    int files = intent.getIntExtra("files_count", 0);
                    int folders = intent.getIntExtra("folders_count", 0);
                    String folder = intent.getStringExtra("folder");
                    lastBroadcastFiles = files;
                    lastBroadcastFolders = folders;

                    updateScanCountersAndStatus(files, folders, folder);
                    if (currentUiMode == 3 && !isSearching) reloadCurrentDirectoryFromDb();
                } else if (ScanService.ACTION_COMPLETED.equals(action)) {
                    int count = intent.getIntExtra("count", 0);
                    updateScanCountersAndStatus(count, lastBroadcastFolders, "הסריקה הסתיימה");
                    switchUiMode(2);
                }
            } catch (Exception ignored) {}
        }
    };

    private final BroadcastReceiver restoreReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();

                if (RestoreService.ACTION_PROGRESS.equals(action)) {
                    int percent = intent.getIntExtra("percent", 0);
                    String eta = intent.getStringExtra("eta");
                    if (currentUiMode == 1) {
                        tvScanStatus.setText("משחזר... (" + percent + "%) · " + eta);
                    }
                } else if (RestoreService.ACTION_COMPLETED.equals(action)) {
                    int success = intent.getIntExtra("success", 0);
                    int failed = intent.getIntExtra("failed", 0);
                    int skipped = intent.getIntExtra("skipped", 0);
                    int foldersCreated = intent.getIntExtra("folders_created", 0);
                    int foldersReused = intent.getIntExtra("folders_reused", 0);
                    showRestoreSummaryDialogAfterService(success, failed, skipped, foldersCreated, foldersReused);
                }
            } catch (Exception ignored) {}
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        try {
            dbHelper = DatabaseHelper.getInstance(this);
            sourceUri = getIntent().getParcelableExtra(EXTRA_URI);
            usbDeviceName = getIntent().getStringExtra(EXTRA_USB_DEVICE_NAME);

            new Thread(() -> {
                currentParentId = dbHelper.getRootId();
                int totalFiles = dbHelper.getTotalFilesCount();
                int totalFolders = dbHelper.getTotalFoldersCount();
                ServiceState state = ServiceState.getInstance();

                new Handler(Looper.getMainLooper()).post(() -> {
                    scanStartTime = System.currentTimeMillis();
                    buildAllUiScreens();

                    if (state.getPhase() == ServiceState.Phase.RESTORING || RestoreService.isRestoreRunning()) {
                        switchUiMode(1);
                        tvScanStatus.setText("משחזר קבצים... (" + state.getPercent() + "%)");
                    } else if (state.getPhase() == ServiceState.Phase.SCANNING || ScanService.isScanRunning()) {
                        switchUiMode(1);
                        lastBroadcastFiles = state.getFilesCount();
                        lastBroadcastFolders = state.getFoldersCount();
                        updateScanCountersAndStatus(lastBroadcastFiles, lastBroadcastFolders, state.getCurrentFolder());
                    } else {
                        if (totalFiles > 0 || totalFolders > 0 || state.getPhase() == ServiceState.Phase.SCAN_COMPLETED) {
                            switchUiMode(2);
                        } else {
                            switchUiMode(1);
                            tvScanStatus.setText("מתחבר לסריקה...");
                            startScanService();
                        }
                    }
                });
            }).start();

        } catch (Throwable t) {
            showExceptionDialog(t);
        }
    }

    private void showExceptionDialog(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        TextView tv = new TextView(this);
        tv.setText(sw.toString());
        tv.setTextSize(12);
        tv.setTextColor(0xFF333333);
        tv.setTextIsSelectable(true);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("שגיאה באתחול המסך")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show();
    }

    private void buildAllUiScreens() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFF8F9FA);

        // --- Screen 1: Scan / Progress Screen ---
        layoutScanScreen = new LinearLayout(this);
        layoutScanScreen.setOrientation(LinearLayout.VERTICAL);
        layoutScanScreen.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layoutScanScreen.setPadding(pad, pad, pad, pad);
        root.addView(layoutScanScreen, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        TextView tvScanHeader = new TextView(this);
        tvScanHeader.setText("סריקת כרטיס זיכרון");
        tvScanHeader.setTextSize(22);
        tvScanHeader.setTextColor(0xFF1976D2);
        tvScanHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        tvScanHeader.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams thLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        thLp.bottomMargin = (int) (32 * getResources().getDisplayMetrics().density);
        layoutScanScreen.addView(tvScanHeader, thLp);

        scanCircularProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        int pSize = (int) (80 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(pSize, pSize);
        pbLp.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        layoutScanScreen.addView(scanCircularProgress, pbLp);

        tvScanStatus = new TextView(this);
        tvScanStatus.setText("מתחבר לסריקה...");
        tvScanStatus.setTextSize(18);
        tvScanStatus.setTextColor(0xFF333333);
        tvScanStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvScanStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.bottomMargin = 12;
        layoutScanScreen.addView(tvScanStatus, stLp);

        tvScanLiveCounters = new TextView(this);
        tvScanLiveCounters.setText("תיקיות: 0 · קבצים: 0 · זמן שעבר: 00:00");
        tvScanLiveCounters.setTextSize(14);
        tvScanLiveCounters.setTextColor(0xFF757575);
        tvScanLiveCounters.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cntLp.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        layoutScanScreen.addView(tvScanLiveCounters, cntLp);

        btnCancelScan = new Button(this);
        btnCancelScan.setText(R.string.cancel_scan);
        btnCancelScan.setTextColor(0xFFD32F2F);
        btnCancelScan.setBackgroundColor(0xFFFFEBEE);
        int btnPad = (int) (14 * getResources().getDisplayMetrics().density);
        btnCancelScan.setPadding(btnPad, btnPad, btnPad, btnPad);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelLp.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        layoutScanScreen.addView(btnCancelScan, cancelLp);

        btnCancelScan.setOnClickListener(v -> {
            if (RestoreService.isRestoreRunning()) {
                Intent intent = new Intent(ScanActivity.this, RestoreService.class);
                intent.setAction(RestoreService.ACTION_CANCEL);
                startService(intent);
            } else {
                Intent intent = new Intent(ScanActivity.this, ScanService.class);
                intent.setAction(ScanService.ACTION_CANCEL);
                startService(intent);
                finish();
            }
        });

        btnToggleTechDetails = new Button(this);
        btnToggleTechDetails.setText("פרטים טכניים ▼");
        btnToggleTechDetails.setTextSize(13);
        btnToggleTechDetails.setTextColor(0xFF1976D2);
        btnToggleTechDetails.setBackgroundColor(0xFFE3F2FD);
        layoutScanScreen.addView(btnToggleTechDetails, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView techScroll = new ScrollView(this);
        LinearLayout.LayoutParams tsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        tsLp.topMargin = 8;
        layoutScanScreen.addView(techScroll, tsLp);

        layoutTechDetailsContainer = new LinearLayout(this);
        layoutTechDetailsContainer.setOrientation(LinearLayout.VERTICAL);
        layoutTechDetailsContainer.setVisibility(View.GONE);
        techScroll.addView(layoutTechDetailsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvTechDetailsContent = new TextView(this);
        tvTechDetailsContent.setTextSize(12);
        tvTechDetailsContent.setTextColor(0xFF444444);
        tvTechDetailsContent.setBackgroundColor(0xFFFFFFFF);
        tvTechDetailsContent.setPadding(12, 12, 12, 12);
        LinearLayout.LayoutParams tdContentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tdContentLp.bottomMargin = 8;
        layoutTechDetailsContainer.addView(tvTechDetailsContent, tdContentLp);

        LinearLayout techBtnRow = new LinearLayout(this);
        techBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        layoutTechDetailsContainer.addView(techBtnRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnCopyReportScan = new Button(this);
        btnCopyReportScan.setText("העתק דוח");
        btnCopyReportScan.setTextSize(11);
        btnCopyReportScan.setTextColor(0xFFFFFFFF);
        btnCopyReportScan.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams br1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        br1.rightMargin = 4;
        techBtnRow.addView(btnCopyReportScan, br1);

        btnSaveReportScan = new Button(this);
        btnSaveReportScan.setText("שמור דוח");
        btnSaveReportScan.setTextSize(11);
        btnSaveReportScan.setTextColor(0xFFFFFFFF);
        btnSaveReportScan.setBackgroundColor(0xFF00838F);
        techBtnRow.addView(btnSaveReportScan, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnToggleTechDetails.setOnClickListener(v -> {
            boolean isVis = layoutTechDetailsContainer.getVisibility() == View.VISIBLE;
            layoutTechDetailsContainer.setVisibility(isVis ? View.GONE : View.VISIBLE);
            btnToggleTechDetails.setText(isVis ? "פרטים טכניים ▼" : "פרטים טכניים ▲");
            if (!isVis) {
                new Thread(() -> {
                    final String report = DiagnosticReporter.generateFullReport(ScanActivity.this);
                    new Handler(Looper.getMainLooper()).post(() -> tvTechDetailsContent.setText(report));
                }).start();
            }
        });

        btnCopyReportScan.setOnClickListener(v -> {
            new Thread(() -> {
                String report = DiagnosticReporter.generateFullReport(ScanActivity.this);
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Scan Report", report));
                            Toast.makeText(ScanActivity.this, "הדוח הועתק ללוח", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ignored) {}
                });
            }).start();
        });

        btnSaveReportScan.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "sd_rescue_report.txt");
            try {
                startActivityForResult(intent, REQUEST_CODE_SAVE_REPORT);
            } catch (Exception ignored) {}
        });

        // --- Screen 2: Results Dashboard ---
        layoutResultsDashboard = new LinearLayout(this);
        layoutResultsDashboard.setOrientation(LinearLayout.VERTICAL);
        layoutResultsDashboard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layoutResultsDashboard.setBackgroundColor(0xFFF8F9FA);
        layoutResultsDashboard.setVisibility(View.GONE);
        root.addView(layoutResultsDashboard);

        topBarResults = new LinearLayout(this);
        topBarResults.setOrientation(LinearLayout.HORIZONTAL);
        topBarResults.setGravity(Gravity.CENTER_VERTICAL);
        topBarResults.setPadding(pad, pad, pad, pad);
        topBarResults.setBackgroundColor(0xFFFFFFFF);
        layoutResultsDashboard.addView(topBarResults, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvResTitle = new TextView(this);
        tvResTitle.setText(R.string.app_name);
        tvResTitle.setTextSize(22);
        tvResTitle.setTextColor(0xFF1976D2);
        tvResTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topBarResults.addView(tvResTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnSearchToggle = new Button(this);
        btnSearchToggle.setText("🔍");
        btnSearchToggle.setTextSize(16);
        btnSearchToggle.setBackgroundColor(0x00000000);
        topBarResults.addView(btnSearchToggle, new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density)));

        btnMenuResults = new Button(this);
        btnMenuResults.setText("⋮");
        btnMenuResults.setTextSize(20);
        btnMenuResults.setTextColor(0xFF333333);
        btnMenuResults.setBackgroundColor(0x00000000);
        topBarResults.addView(btnMenuResults, new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density)));

        etSearchDashboard = new EditText(this);
        etSearchDashboard.setHint("חיפוש קבצים לפי שם...");
        etSearchDashboard.setTextSize(14);
        etSearchDashboard.setPadding(pad, pad, pad, pad);
        etSearchDashboard.setBackgroundColor(0xFFF1F3F4);
        etSearchDashboard.setVisibility(View.GONE);
        layoutResultsDashboard.addView(etSearchDashboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        etSearchDashboard.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                final String q = s.toString();
                searchRunnable = () -> {
                    if (!q.trim().isEmpty()) {
                        switchUiMode(3);
                        etSearchBrowser.setText(q);
                        performSearch(q);
                    }
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSearchToggle.setOnClickListener(v -> {
            boolean vis = etSearchDashboard.getVisibility() == View.VISIBLE;
            etSearchDashboard.setVisibility(vis ? View.GONE : View.VISIBLE);
            if (!vis) etSearchDashboard.requestFocus();
        });

        btnMenuResults.setOnClickListener(v -> showScanDetailsDialog());

        dashboardLoadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        LinearLayout.LayoutParams dProgLp = new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density));
        dProgLp.gravity = Gravity.CENTER;
        dProgLp.topMargin = 32;
        layoutResultsDashboard.addView(dashboardLoadingProgress, dProgLp);
        dashboardLoadingProgress.setVisibility(View.GONE);

        dashboardScrollContainer = new ScrollView(this);
        layoutResultsDashboard.addView(dashboardScrollContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout dashboardContent = new LinearLayout(this);
        dashboardContent.setOrientation(LinearLayout.VERTICAL);
        dashboardContent.setPadding(pad, pad, pad, pad);
        dashboardScrollContainer.addView(dashboardContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        categoriesGridContainer = new LinearLayout(this);
        categoriesGridContainer.setOrientation(LinearLayout.VERTICAL);
        dashboardContent.addView(categoriesGridContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        cardBrowseFolders = new LinearLayout(this);
        cardBrowseFolders.setOrientation(LinearLayout.VERTICAL);
        cardBrowseFolders.setPadding(pad, pad, pad, pad);
        cardBrowseFolders.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        browseLp.topMargin = pad;
        dashboardContent.addView(cardBrowseFolders, browseLp);

        TextView tvBrowseTitle = new TextView(this);
        tvBrowseTitle.setText("📁 עיון בכל התיקיות");
        tvBrowseTitle.setTextSize(18);
        tvBrowseTitle.setTextColor(0xFF1976D2);
        tvBrowseTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams btParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btParams.bottomMargin = 4;
        cardBrowseFolders.addView(tvBrowseTitle, btParams);

        TextView tvBrowseSub = new TextView(this);
        tvBrowseSub.setText("צפה במבנה התיקיות המלא של הכרטיס ובחר קבצים בודדים לשחזור");
        tvBrowseSub.setTextSize(13);
        tvBrowseSub.setTextColor(0xFF757575);
        cardBrowseFolders.addView(tvBrowseSub);

        cardBrowseFolders.setOnClickListener(v -> {
            switchUiMode(3);
            new Thread(() -> {
                currentParentId = dbHelper.getRootId();
                currentDirPath = "/";
                reloadCurrentDirectoryFromDb();
            }).start();
        });

        // --- Screen 3: Folder Browser Screen ---
        layoutFolderBrowser = new LinearLayout(this);
        layoutFolderBrowser.setOrientation(LinearLayout.VERTICAL);
        layoutFolderBrowser.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        layoutFolderBrowser.setBackgroundColor(0xFFF8F9FA);
        layoutFolderBrowser.setVisibility(View.GONE);
        root.addView(layoutFolderBrowser);

        LinearLayout browserTopBar = new LinearLayout(this);
        browserTopBar.setOrientation(LinearLayout.VERTICAL);
        browserTopBar.setPadding(pad, pad, pad, pad);
        browserTopBar.setBackgroundColor(0xFFFFFFFF);
        layoutFolderBrowser.addView(browserTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        etSearchBrowser = new EditText(this);
        etSearchBrowser.setHint("חיפוש קבצים לפי שם...");
        etSearchBrowser.setTextSize(14);
        etSearchBrowser.setPadding(pad, pad, pad, pad);
        etSearchBrowser.setBackgroundColor(0xFFF1F3F4);
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbParams.bottomMargin = 8;
        browserTopBar.addView(etSearchBrowser, sbParams);

        etSearchBrowser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                final String q = s.toString();
                searchRunnable = () -> performSearch(q);
                searchHandler.postDelayed(searchRunnable, 300);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        layoutBreadcrumb = new LinearLayout(this);
        layoutBreadcrumb.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bcParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bcParams.bottomMargin = 8;
        browserTopBar.addView(layoutBreadcrumb, bcParams);
        updateBreadcrumbBar();

        LinearLayout selActionLayout = new LinearLayout(this);
        selActionLayout.setOrientation(LinearLayout.HORIZONTAL);
        selActionLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams saLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        browserTopBar.addView(selActionLayout, saLp);

        btnSelectAllBrowser = new Button(this);
        btnSelectAllBrowser.setText(R.string.select_all);
        btnSelectAllBrowser.setTextSize(12);
        btnSelectAllBrowser.setTextColor(0xFFFFFFFF);
        btnSelectAllBrowser.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams bSelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        bSelLp.rightMargin = 4;
        selActionLayout.addView(btnSelectAllBrowser, bSelLp);

        btnDeselectAllBrowser = new Button(this);
        btnDeselectAllBrowser.setText(R.string.deselect_all);
        btnDeselectAllBrowser.setTextSize(12);
        btnDeselectAllBrowser.setTextColor(0xFF333333);
        btnDeselectAllBrowser.setBackgroundColor(0xFFE0E0E0);
        selActionLayout.addView(btnDeselectAllBrowser, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        browserLoadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        LinearLayout.LayoutParams bProgLp = new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density),
                (int)(48 * getResources().getDisplayMetrics().density));
        bProgLp.gravity = Gravity.CENTER;
        bProgLp.topMargin = 32;
        layoutFolderBrowser.addView(browserLoadingProgress, bProgLp);
        browserLoadingProgress.setVisibility(View.GONE);

        listViewBrowser = new ListView(this);
        layoutFolderBrowser.addView(listViewBrowser, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        browserAdapter = new TreeEntryAdapter();
        listViewBrowser.setAdapter(browserAdapter);

        bottomBarRestore = new LinearLayout(this);
        bottomBarRestore.setOrientation(LinearLayout.HORIZONTAL);
        bottomBarRestore.setGravity(Gravity.CENTER_VERTICAL);
        bottomBarRestore.setPadding(pad, pad, pad, pad);
        bottomBarRestore.setBackgroundColor(0xFFFFFFFF);
        bottomBarRestore.setVisibility(View.GONE);
        layoutFolderBrowser.addView(bottomBarRestore, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvRestoreSelectionInfo = new TextView(this);
        tvRestoreSelectionInfo.setTextSize(14);
        tvRestoreSelectionInfo.setTextColor(0xFF1976D2);
        tvRestoreSelectionInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        bottomBarRestore.addView(tvRestoreSelectionInfo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnExecuteRestore = new Button(this);
        btnExecuteRestore.setText("שחזר");
        btnExecuteRestore.setTextColor(0xFFFFFFFF);
        btnExecuteRestore.setBackgroundColor(0xFF388E3C);
        bottomBarRestore.addView(btnExecuteRestore, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnSelectAllBrowser.setOnClickListener(v -> {
            browserLoadingProgress.setVisibility(View.VISIBLE);
            new Thread(() -> {
                dbHelper.setAllSelected(true);
                updateSelectionCounterAndBottomBar();
                reloadCurrentDirectoryFromDb();
            }).start();
        });

        btnDeselectAllBrowser.setOnClickListener(v -> {
            browserLoadingProgress.setVisibility(View.VISIBLE);
            new Thread(() -> {
                dbHelper.setAllSelected(false);
                updateSelectionCounterAndBottomBar();
                reloadCurrentDirectoryFromDb();
            }).start();
        });

        btnExecuteRestore.setOnClickListener(v -> promptForSaveDirectory(false));
    }

    private void switchUiMode(int mode) {
        currentUiMode = mode;
        layoutScanScreen.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        layoutResultsDashboard.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        layoutFolderBrowser.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);

        if (mode == 2) {
            populateDashboardCategories();
        }
    }

    private void populateDashboardCategories() {
        dashboardLoadingProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            Map<String, long[]> statsMap = dbHelper.getCategoryStats();
            List<CatCardInfo> cats = new ArrayList<>();
            cats.add(new CatCardInfo(FileCategory.PHOTOS, "תמונות (Photos)", "🖼️", statsMap));
            cats.add(new CatCardInfo(FileCategory.VIDEOS, "סרטונים (Videos)", "🎬", statsMap));
            cats.add(new CatCardInfo(FileCategory.MUSIC, "מוזיקה ואודיו (Music)", "🎵", statsMap));
            cats.add(new CatCardInfo(FileCategory.DOCUMENTS, "מסמכים (Documents)", "📄", statsMap));
            cats.add(new CatCardInfo(FileCategory.ARCHIVES_APPS, "ארכיונים ואפליקציות", "📦", statsMap));
            cats.add(new CatCardInfo(FileCategory.OTHER, "אחר (Other)", "📁", statsMap));

            new Handler(Looper.getMainLooper()).post(() -> {
                dashboardLoadingProgress.setVisibility(View.GONE);
                categoriesGridContainer.removeAllViews();
                int pad = (int) (8 * getResources().getDisplayMetrics().density);

                for (int i = 0; i < cats.size(); i += 2) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowLp.bottomMargin = pad;
                    categoriesGridContainer.addView(row, rowLp);

                    CatCardInfo c1 = cats.get(i);
                    View card1 = createCategoryCardView(c1);
                    LinearLayout.LayoutParams cLp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    cLp1.rightMargin = pad;
                    row.addView(card1, cLp1);

                    if (i + 1 < cats.size()) {
                        CatCardInfo c2 = cats.get(i + 1);
                        View card2 = createCategoryCardView(c2);
                        LinearLayout.LayoutParams cLp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                        cLp2.leftMargin = pad;
                        row.addView(card2, cLp2);
                    } else {
                        View spacer = new View(this);
                        row.addView(spacer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                    }
                }
            });
        }).start();
    }

    private static class CatCardInfo {
        final String key;
        final String name;
        final String emoji;
        final long count;
        final long size;
        CatCardInfo(String key, String name, String emoji, Map<String, long[]> statsMap) {
            this.key = key;
            this.name = name;
            this.emoji = emoji;
            long[] val = statsMap.get(key);
            this.count = val != null ? val[0] : 0;
            this.size = val != null ? val[1] : 0;
        }
    }

    private View createCategoryCardView(final CatCardInfo cat) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(0xFFFFFFFF);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(cat.emoji);
        tvEmoji.setTextSize(24);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eLp.bottomMargin = 8;
        card.addView(tvEmoji, eLp);

        TextView tvName = new TextView(this);
        tvName.setText(cat.name);
        tvName.setTextSize(15);
        tvName.setTextColor(0xFF333333);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nLp.bottomMargin = 4;
        card.addView(tvName, nLp);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(String.format(Locale.getDefault(), "%d קבצים • %s", cat.count, formatSize(cat.size)));
        tvMeta.setTextSize(12);
        tvMeta.setTextColor(0xFF757575);
        card.addView(tvMeta);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(ScanActivity.this, CategoryFilesActivity.class);
            intent.putExtra(CategoryFilesActivity.EXTRA_CATEGORY_KEY, cat.key);
            intent.putExtra(CategoryFilesActivity.EXTRA_CATEGORY_NAME, cat.name);
            if (sourceUri != null) intent.putExtra(CategoryFilesActivity.EXTRA_URI, sourceUri);
            if (usbDeviceName != null) intent.putExtra(CategoryFilesActivity.EXTRA_USB_DEVICE_NAME, usbDeviceName);
            startActivity(intent);
        });

        return card;
    }

    private void updateScanCountersAndStatus(int files, int folders, String folder) {
        long elapsedSec = (System.currentTimeMillis() - scanStartTime) / 1000;
        String elapsedStr = String.format(Locale.getDefault(), "%02d:%02d", elapsedSec / 60, elapsedSec % 60);
        if (tvScanLiveCounters != null) {
            tvScanLiveCounters.setText(String.format(Locale.getDefault(), "תיקיות: %d · קבצים: %d · זמן שעבר: %s", folders, files, elapsedStr));
        }
        if (folder != null && tvScanStatus != null) {
            tvScanStatus.setText("סורק: " + folder);
        }
    }

    private void performSearch(final String query) {
        if (query == null || query.trim().isEmpty()) {
            isSearching = false;
            reloadCurrentDirectoryFromDb();
            return;
        }

        isSearching = true;
        browserLoadingProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            final List<RecoveredEntry> results = dbHelper.searchNodes(query);
            new Handler(Looper.getMainLooper()).post(() -> {
                browserLoadingProgress.setVisibility(View.GONE);
                searchResults = results;
                currentDirEntries = searchResults;
                browserAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void updateSelectionCounterAndBottomBar() {
        new Thread(() -> {
            final long[] stats = dbHelper.getSelectedStats();
            new Handler(Looper.getMainLooper()).post(() -> {
                long files = stats[0];
                long bytes = stats[1];
                if (files > 0) {
                    bottomBarRestore.setVisibility(View.VISIBLE);
                    tvRestoreSelectionInfo.setText(String.format(Locale.getDefault(), "%d פריטים · %s", files, formatSize(bytes)));
                } else {
                    bottomBarRestore.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void reloadCurrentDirectoryFromDb() {
        if (isSearching) return;
        browserLoadingProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            final List<RecoveredEntry> list = dbHelper.getChildrenOfDirectory(currentParentId);
            new Handler(Looper.getMainLooper()).post(() -> {
                browserLoadingProgress.setVisibility(View.GONE);
                currentDirEntries = list;
                updateBreadcrumbBar();
                updateSelectionCounterAndBottomBar();
                browserAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void updateBreadcrumbBar() {
        if (layoutBreadcrumb == null) return;
        layoutBreadcrumb.removeAllViews();

        TextView tvRoot = new TextView(this);
        tvRoot.setText("📁 /");
        tvRoot.setTextSize(14);
        tvRoot.setTextColor(0xFF1976D2);
        tvRoot.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRoot.setPadding(4, 4, 4, 4);
        tvRoot.setOnClickListener(v -> {
            isSearching = false;
            etSearchBrowser.setText("");
            currentDirPath = "/";
            new Thread(() -> {
                currentParentId = dbHelper.getRootId();
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateBreadcrumbBar();
                    reloadCurrentDirectoryFromDb();
                });
            }).start();
        });
        layoutBreadcrumb.addView(tvRoot);

        if (currentDirPath == null || currentDirPath.equals("/")) return;

        String[] parts = currentDirPath.split("/");
        String pathSoFar = "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            pathSoFar += "/" + part;
            final String targetPath = pathSoFar;

            TextView tvSep = new TextView(this);
            tvSep.setText(" > ");
            tvSep.setTextSize(14);
            tvSep.setTextColor(0xFF757575);
            layoutBreadcrumb.addView(tvSep);

            TextView tvPart = new TextView(this);
            tvPart.setText(part);
            tvPart.setTextSize(14);
            tvPart.setTextColor(0xFF1976D2);
            tvPart.setTypeface(null, android.graphics.Typeface.BOLD);
            tvPart.setPadding(4, 4, 4, 4);
            tvPart.setOnClickListener(v -> {
                isSearching = false;
                etSearchBrowser.setText("");
                currentDirPath = targetPath;
                new Thread(() -> {
                    long foundId = dbHelper.getNodeId(targetPath.substring(0, targetPath.lastIndexOf('/')), part);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (foundId != -1) {
                            currentParentId = foundId;
                        }
                        updateBreadcrumbBar();
                        reloadCurrentDirectoryFromDb();
                    });
                }).start();
            });
            layoutBreadcrumb.addView(tvPart);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentUiMode == 3) {
            if (isSearching) {
                isSearching = false;
                etSearchBrowser.setText("");
                reloadCurrentDirectoryFromDb();
                return;
            }
            if (!currentDirPath.equals("/")) {
                int lastSlash = currentDirPath.lastIndexOf('/');
                String parentPath;
                String folderName;
                if (lastSlash <= 0) {
                    parentPath = "/";
                    folderName = currentDirPath.substring(1);
                    currentDirPath = "/";
                    new Thread(() -> {
                        currentParentId = dbHelper.getRootId();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            updateBreadcrumbBar();
                            reloadCurrentDirectoryFromDb();
                        });
                    }).start();
                } else {
                    parentPath = currentDirPath.substring(0, lastSlash);
                    folderName = currentDirPath.substring(lastSlash + 1);
                    currentDirPath = parentPath;
                    new Thread(() -> {
                        long pid = dbHelper.getParentId(parentPath, folderName);
                        long rootId = dbHelper.getRootId();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (pid != -1) currentParentId = pid;
                            else currentParentId = rootId;
                            updateBreadcrumbBar();
                            reloadCurrentDirectoryFromDb();
                        });
                    }).start();
                }
            } else {
                switchUiMode(2);
            }
        } else if (currentUiMode == 2) {
            super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    private void startScanService() {
        Intent intent = new Intent(this, ScanService.class);
        if (sourceUri != null) {
            intent.putExtra(ScanService.EXTRA_URI, sourceUri);
        } else if (usbDeviceName != null) {
            intent.putExtra(ScanService.EXTRA_USB_DEVICE_NAME, usbDeviceName);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScanService.ACTION_PROGRESS);
        filter.addAction(ScanService.ACTION_COMPLETED);
        filter.addAction(RestoreService.ACTION_PROGRESS);
        filter.addAction(RestoreService.ACTION_COMPLETED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED);
            registerReceiver(restoreReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
            registerReceiver(restoreReceiver, filter);
        }
        if (currentUiMode == 3 && !isSearching) reloadCurrentDirectoryFromDb();
        if (currentUiMode == 2) populateDashboardCategories();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(scanReceiver);
            unregisterReceiver(restoreReceiver);
        } catch (Exception ignored) {}
    }

    private void promptForSaveDirectory(boolean saveAll) {
        this.saveAllModeFlag = saveAll;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String savedUriStr = prefs.getString(PREF_DEST_URI, null);
        if (savedUriStr != null) {
            try {
                Uri initialUri = Uri.parse(savedUriStr);
                if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
                }
            } catch (Exception ignored) {}
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_SAVE_DIR);
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בפתיחת בורר התיקיות", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAVE_DIR && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                final Uri treeUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putString(PREF_DEST_URI, treeUri.toString()).apply();
                } catch (Exception ignored) {}

                startRestoreService(treeUri, saveAllModeFlag);
            }
        } else if (requestCode == REQUEST_CODE_SAVE_REPORT && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                writeReportToFile(data.getData());
            }
        }
    }

    private void writeReportToFile(Uri uri) {
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    String report = DiagnosticReporter.generateFullReport(this);
                    out.write(report.getBytes(StandardCharsets.UTF_8));
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(this, "הדוח נשמר בהצלחה כקובץ טקסט", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void startRestoreService(Uri treeUri, boolean saveAll) {
        Toast.makeText(this, "מתחיל שחזור קבצים ברקע...", Toast.LENGTH_SHORT).show();
        RestoreUtils.startRestoreService(this, treeUri, saveAll, sourceUri, usbDeviceName);
    }

    private void showScanDetailsDialog() {
        new Thread(() -> {
            String report = DiagnosticReporter.generateFullReport(this);
            new Handler(Looper.getMainLooper()).post(() -> {
                TextView tv = new TextView(this);
                tv.setText(report);
                tv.setTextSize(12);
                tv.setTextColor(0xFF333333);
                tv.setTextIsSelectable(true);
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad, pad, pad);

                ScrollView sv = new ScrollView(this);
                sv.addView(tv);

                new AlertDialog.Builder(this)
                        .setTitle("פרטי הסריקה (Scan Details)")
                        .setView(sv)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }).start();
    }

    private void showRestoreSummaryDialogAfterService(int success, int failed, int skipped, int foldersCreated, int foldersReused) {
        StringBuilder sb = new StringBuilder();
        sb.append("סיכום תהליך שחזור:\n\n");
        sb.append("• קבצים ששוחזרו בהצלחה: ").append(success).append("\n");
        sb.append("• קבצים שדולגו (כבר קיימים): ").append(skipped).append("\n");
        sb.append("• קבצים שנכשלו: ").append(failed).append("\n");
        sb.append("• תיקיות שנוצרו: ").append(foldersCreated).append("\n");
        sb.append("• תיקיות שנעשה בהן שימוש חוזר: ").append(foldersReused).append("\n\n");

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextSize(12);
        tv.setTextColor(0xFF333333);
        tv.setTextIsSelectable(true);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("דוח שחזור קבצים")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showRawBytesHexDialog(RecoveredEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== אבחון פרטי צומת (Node Details) ===\n");
        sb.append("• Node ID: ").append(entry.getId()).append("\n");
        sb.append("• Parent ID: ").append(entry.getParentId()).append("\n");
        sb.append("• First Cluster: ").append(entry.getFirstCluster()).append("\n");
        sb.append("• Stored Offset: 0x").append(Long.toHexString(entry.getOffset())).append("\n\n");
        sb.append("שם הקובץ: ").append(entry.getName()).append("\n");
        sb.append("נתיב: ").append(entry.getPath()).append("\n");
        sb.append("גודל: ").append(entry.getLength()).append(" בייטס\n");
        sb.append("סוג: ").append(FileType.safeName(entry.getType())).append("\n\n");

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextSize(12);
        tv.setTextColor(0xFF333333);
        tv.setTextIsSelectable(true);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("פרטי קובץ גולמיים")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private class TreeEntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return currentDirEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return currentDirEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            RecoveredEntry item = currentDirEntries.get(position);
            return item != null ? item.getId() : position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            CheckBox checkBox;
            ImageView ivIcon;
            TextView tvName;
            TextView tvDetails;

            if (convertView == null) {
                // Flat row layout with a single container to prevent nested layout performance overhead
                itemLayout = new LinearLayout(ScanActivity.this);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                itemLayout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                itemLayout.setPadding(pad, pad, pad, pad);
                itemLayout.setBackgroundColor(0xFFFFFFFF);

                checkBox = new CheckBox(ScanActivity.this);
                checkBox.setFocusable(false);
                checkBox.setClickable(true);
                itemLayout.addView(checkBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                ivIcon = new ImageView(ScanActivity.this);
                int iconSize = (int) (36 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(iconSize, iconSize);
                imgParams.leftMargin = pad;
                imgParams.rightMargin = pad;
                ivIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemLayout.addView(ivIcon, imgParams);

                LinearLayout textLayout = new LinearLayout(ScanActivity.this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.addView(textLayout, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

                tvName = new TextView(ScanActivity.this);
                tvName.setTextSize(15);
                tvName.setTextColor(0xFF333333);
                textLayout.addView(tvName);

                tvDetails = new TextView(ScanActivity.this);
                tvDetails.setTextSize(13);
                tvDetails.setTextColor(0xFF757575);
                textLayout.addView(tvDetails);

                itemLayout.setTag(new TreeEntryViewHolder(checkBox, ivIcon, tvName, tvDetails));
            } else {
                itemLayout = (LinearLayout) convertView;
                TreeEntryViewHolder holder = (TreeEntryViewHolder) itemLayout.getTag();
                checkBox = holder.checkBox;
                ivIcon = holder.ivIcon;
                tvName = holder.tvName;
                tvDetails = holder.tvDetails;
            }

            final RecoveredEntry entry = currentDirEntries.get(position);
            if (entry == null) return itemLayout;

            checkBox.setChecked(entry.isSelected());
            checkBox.setOnClickListener(v -> {
                boolean newState = checkBox.isChecked();
                browserLoadingProgress.setVisibility(View.VISIBLE);
                new Thread(() -> {
                    dbHelper.setNodeAndSubtreeSelected(entry, newState ? 1 : 0);
                    updateSelectionCounterAndBottomBar();
                    reloadCurrentDirectoryFromDb();
                }).start();
            });

            if (entry.isDirectory()) {
                ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
                tvName.setText("📁 " + entry.getName());
                String folderDesc = String.format(Locale.getDefault(), "%d קבצים • %s", entry.getCachedRecursiveFileCount(), formatSize(entry.getCachedRecursiveSize()));
                tvDetails.setText(folderDesc);
            } else {
                tvName.setText(entry.getName());
                String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(entry.getModifiedTime()));
                tvDetails.setText("גודל: " + formatSize(entry.getLength()) + " • " + dateStr);

                ivIcon.setImageBitmap(null);
                ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            itemLayout.setOnClickListener(v -> {
                if (entry.isDirectory()) {
                    isSearching = false;
                    etSearchBrowser.setText("");
                    currentParentId = entry.getId();
                    currentDirPath = currentDirPath.equals("/") ? "/" + entry.getName() : currentDirPath + "/" + entry.getName();
                    updateBreadcrumbBar();
                    reloadCurrentDirectoryFromDb();
                } else {
                    showRawBytesHexDialog(entry);
                }
            });

            return itemLayout;
        }

        class TreeEntryViewHolder {
            final CheckBox checkBox;
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvDetails;

            TreeEntryViewHolder(CheckBox checkBox, ImageView ivIcon, TextView tvName, TextView tvDetails) {
                this.checkBox = checkBox;
                this.ivIcon = ivIcon;
                this.tvName = tvName;
                this.tvDetails = tvDetails;
            }
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 בייט";
        final String[] units = new String[]{"בייט", "קילובייט", "מגהבייט", "ג׳יגהבייט", "טרהבייט"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
