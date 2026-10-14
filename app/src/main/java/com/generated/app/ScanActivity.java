package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanActivity extends Activity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    private static final int REQUEST_CODE_SAVE_DIR = 2002;
    private static final int REQUEST_CODE_SAVE_REPORT = 2003;
    private static final String PREF_DEST_URI = "pref_dest_uri";

    private Uri sourceUri;
    private String usbDeviceName;
    private ByteSource byteSource;
    private DatabaseHelper dbHelper;

    private EditText etSearch;
    private LinearLayout layoutBreadcrumb;
    private Button btnSelectAll;
    private Button btnClearSelection;
    private TextView tvSelectionCounter;
    private TextView tvFolderStatsLine;

    private ListView listView;

    private Button btnSaveSelected;
    private Button btnSaveAll;

    private LinearLayout headerContainer;
    private TextView tvStatus;
    private TextView tvMethodUsed;
    private TextView tvSummaryBox;
    private Button btnCopyReport;
    private Button btnSaveReport;
    private Button btnDeepScanNow;
    private Button btnErrorLog;
    private Button btnTestRestore;
    private ProgressBar progressBar;
    private Button btnCancel;
    private Button btnPauseResume;
    private Button btnToggleDetails;

    private String currentDirPath = "/";
    private long currentParentId = 0;
    private List<RecoveredEntry> currentDirEntries = new ArrayList<>();
    private List<RecoveredEntry> searchResults = new ArrayList<>();
    private boolean isSearching = false;
    private TreeEntryAdapter adapter;

    private boolean isPaused = false;
    private boolean isDeepScanning = false;
    private boolean detailsExpanded = true;

    private int lastBroadcastFiles = 0;
    private int lastBroadcastFolders = 0;

    private boolean saveAllModeFlag = true;
    private boolean isTestRestoreFlag = false;

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

                    if (folder != null) {
                        tvStatus.setText("סורק תיקייה: " + folder + " • נמצאו: " + (files + folders));
                    } else {
                        tvStatus.setText("סורק קבצים... • נמצאו: " + (files + folders));
                    }
                    updateSummaryBoxWithCounts(files, folders);
                    updateSelectionCounter();
                    if (!isSearching) reloadCurrentDirectoryFromDb();
                } else if (ScanService.ACTION_COMPLETED.equals(action)) {
                    int count = intent.getIntExtra("count", 0);
                    tvStatus.setText("הסריקה הסתיימה. נמצאו " + count + " פריטים.");
                    btnCancel.setText(android.R.string.ok);
                    btnPauseResume.setVisibility(View.GONE);
                    tvMethodUsed.setText("שיטת שחזור: הושלם (" + FilesystemParser.getRecoveryMethodUsed() + ")");
                    updateSummaryBox();
                    updateSelectionCounter();
                    if (!isSearching) reloadCurrentDirectoryFromDb();
                    checkOrphansAndWarn();
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
                    tvStatus.setText("משחזר... (" + percent + "%) · " + eta);
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(percent);
                } else if (RestoreService.ACTION_COMPLETED.equals(action)) {
                    int success = intent.getIntExtra("success", 0);
                    int failed = intent.getIntExtra("failed", 0);
                    int skipped = intent.getIntExtra("skipped", 0);
                    int foldersCreated = intent.getIntExtra("folders_created", 0);
                    int foldersReused = intent.getIntExtra("folders_reused", 0);
                    tvStatus.setText(String.format(Locale.getDefault(), "השחזור הסתיים: %d שוחזרו, %d דולגו, %d נכשלו", success, skipped, failed));
                    progressBar.setProgress(100);
                    showRestoreSummaryDialogAfterService(success, failed, skipped, foldersCreated, foldersReused);
                }
            } catch (Exception ignored) {}
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        dbHelper = DatabaseHelper.getInstance(this);

        if (dbHelper.isOlderVersionDb()) {
            new AlertDialog.Builder(this)
                    .setTitle("גרסה ישנה")
                    .setMessage("Results are from an older version, please scan again")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        currentParentId = dbHelper.getRootId();
        sourceUri = getIntent().getParcelableExtra(EXTRA_URI);
        usbDeviceName = getIntent().getStringExtra(EXTRA_USB_DEVICE_NAME);

        if (sourceUri == null && usbDeviceName == null) {
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            if (sourceUri != null) {
                byteSource = new FileImageSource(getContentResolver(), sourceUri);
            } else {
                android.hardware.usb.UsbManager usbManager = (android.hardware.usb.UsbManager) getSystemService(Context.USB_SERVICE);
                android.hardware.usb.UsbDevice target = null;
                for (android.hardware.usb.UsbDevice d : usbManager.getDeviceList().values()) {
                    if (d.getDeviceName().equals(usbDeviceName)) {
                        target = d;
                        break;
                    }
                }
                if (target != null) {
                    byteSource = new UsbMassStorageSource(this, target);
                }
            }
        } catch (Exception ignored) {}

        setupUI();

        if (RestoreService.isRestoreRunning()) {
            tvStatus.setText("משחזר קבצים... (" + RestoreService.getCurrentPercent() + "%) · " + RestoreService.getCurrentEtaText());
            progressBar.setIndeterminate(false);
            progressBar.setProgress(RestoreService.getCurrentPercent());
        } else if (!ScanService.isScanRunning()) {
            startScanService();
        } else {
            lastBroadcastFiles = ScanService.getFilesCount();
            lastBroadcastFolders = ScanService.getFoldersCount();
            tvStatus.setText("סורק... • נמצאו: " + (lastBroadcastFiles + lastBroadcastFolders));
            updateSummaryBoxWithCounts(lastBroadcastFiles, lastBroadcastFolders);
        }
    }

    private void checkOrphansAndWarn() {
        int orphans = dbHelper.getOrphansCount();
        if (orphans > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("אזהרת יתומים (Orphan Warning)")
                    .setMessage("זוהו " + orphans + " קבצים/תיקיות יתומים (orphans) ללא אב תואם בטבלה.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFF8F9FA);

        int pad = (int) (12 * getResources().getDisplayMetrics().density);

        LinearLayout fixedTopBar = new LinearLayout(this);
        fixedTopBar.setOrientation(LinearLayout.VERTICAL);
        fixedTopBar.setPadding(pad, pad, pad, pad);
        fixedTopBar.setBackgroundColor(0xFFFFFFFF);
        root.addView(fixedTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        etSearch = new EditText(this);
        etSearch.setHint("חיפוש קבצים לפי שם...");
        etSearch.setTextSize(14);
        etSearch.setPadding(pad, pad, pad, pad);
        etSearch.setBackgroundColor(0xFFF1F3F4);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchParams.bottomMargin = 8;
        fixedTopBar.addView(etSearch, searchParams);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                final String query = s.toString();
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        performSearch(query);
                    }
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        layoutBreadcrumb = new LinearLayout(this);
        layoutBreadcrumb.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bcParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bcParams.bottomMargin = 8;
        fixedTopBar.addView(layoutBreadcrumb, bcParams);
        updateBreadcrumbBar();

        tvFolderStatsLine = new TextView(this);
        tvFolderStatsLine.setTextSize(13);
        tvFolderStatsLine.setTextColor(0xFF333333);
        tvFolderStatsLine.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams statsLineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statsLineParams.bottomMargin = 8;
        fixedTopBar.addView(tvFolderStatsLine, statsLineParams);

        LinearLayout selActionLayout = new LinearLayout(this);
        selActionLayout.setOrientation(LinearLayout.HORIZONTAL);
        selActionLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        selParams.bottomMargin = 4;
        fixedTopBar.addView(selActionLayout, selParams);

        btnSelectAll = new Button(this);
        btnSelectAll.setText(R.string.select_all);
        btnSelectAll.setTextSize(12);
        btnSelectAll.setTextColor(0xFFFFFFFF);
        btnSelectAll.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams btnSelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnSelLp.rightMargin = 4;
        selActionLayout.addView(btnSelectAll, btnSelLp);

        btnClearSelection = new Button(this);
        btnClearSelection.setText(R.string.deselect_all);
        btnClearSelection.setTextSize(12);
        btnClearSelection.setTextColor(0xFF333333);
        btnClearSelection.setBackgroundColor(0xFFE0E0E0);
        selActionLayout.addView(btnClearSelection, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        tvSelectionCounter = new TextView(this);
        tvSelectionCounter.setTextSize(13);
        tvSelectionCounter.setTextColor(0xFF1976D2);
        tvSelectionCounter.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fixedTopBar.addView(tvSelectionCounter, counterParams);
        updateSelectionCounter();

        btnSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dbHelper.setAllSelected(true);
                updateSelectionCounter();
                reloadCurrentDirectoryFromDb();
            }
        });

        btnClearSelection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dbHelper.setAllSelected(false);
                updateSelectionCounter();
                reloadCurrentDirectoryFromDb();
            }
        });

        listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        root.addView(listView, listParams);

        setupListViewHeader();

        adapter = new TreeEntryAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int headerCount = listView.getHeaderViewsCount();
                if (position < headerCount) return;
                int actualIndex = position - headerCount;
                if (actualIndex >= currentDirEntries.size()) return;

                RecoveredEntry clicked = currentDirEntries.get(actualIndex);
                if (clicked != null && clicked.isDirectory()) {
                    isSearching = false;
                    etSearch.setText("");
                    currentParentId = clicked.getId();
                    currentDirPath = currentDirPath.equals("/") ? "/" + clicked.getName() : currentDirPath + "/" + clicked.getName();
                    updateBreadcrumbBar();
                    reloadCurrentDirectoryFromDb();
                } else if (clicked != null && !clicked.isDirectory()) {
                    showRawBytesHexDialog(clicked);
                }
            }
        });

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(pad, pad, pad, pad);
        bottomBar.setBackgroundColor(0xFFFFFFFF);
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnSaveSelected = new Button(this);
        btnSaveSelected.setText("שחזר נבחרים");
        btnSaveSelected.setTextColor(0xFFFFFFFF);
        btnSaveSelected.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        saveParams.rightMargin = 8;
        bottomBar.addView(btnSaveSelected, saveParams);

        btnSaveAll = new Button(this);
        btnSaveAll.setText(R.string.save_all);
        btnSaveAll.setTextColor(0xFFFFFFFF);
        btnSaveAll.setBackgroundColor(0xFF388E3C);
        bottomBar.addView(btnSaveAll, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        btnSaveSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForSaveDirectory(false, false);
            }
        });

        btnSaveAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForSaveDirectory(true, false);
            }
        });

        setContentView(root);
        reloadCurrentDirectoryFromDb();
    }

    private void setupListViewHeader() {
        headerContainer = new LinearLayout(this);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        headerContainer.setPadding(pad, pad, pad, pad);
        headerContainer.setBackgroundColor(0xFFFFFFFF);

        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(pad, pad, pad, pad);
        topCard.setBackgroundColor(0xFFF1F3F4);
        headerContainer.addView(topCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnToggleDetails = new Button(this);
        btnToggleDetails.setText("הסתר פרטי סריקה ▲");
        btnToggleDetails.setTextSize(12);
        btnToggleDetails.setTextColor(0xFF1976D2);
        btnToggleDetails.setBackgroundColor(0xFFE3F2FD);
        LinearLayout.LayoutParams togParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        togParams.bottomMargin = 8;
        topCard.addView(btnToggleDetails, togParams);

        final LinearLayout detailsContent = new LinearLayout(this);
        detailsContent.setOrientation(LinearLayout.VERTICAL);
        topCard.addView(detailsContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnToggleDetails.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detailsExpanded = !detailsExpanded;
                detailsContent.setVisibility(detailsExpanded ? View.VISIBLE : View.GONE);
                btnToggleDetails.setText(detailsExpanded ? "הסתר פרטי סריקה ▲" : "הצג פרטי סריקה ▼");
            }
        });

        tvStatus = new TextView(this);
        tvStatus.setText(R.string.scanning_title);
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(0xFF333333);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.bottomMargin = 4;
        detailsContent.addView(tvStatus, statusParams);

        tvMethodUsed = new TextView(this);
        tvMethodUsed.setText("שיטת שחזור: מנתח מערכת קבצים...");
        tvMethodUsed.setTextSize(12);
        tvMethodUsed.setTextColor(0xFF00838F);
        LinearLayout.LayoutParams methodParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        methodParams.bottomMargin = 6;
        detailsContent.addView(tvMethodUsed, methodParams);

        tvSummaryBox = new TextView(this);
        tvSummaryBox.setTextSize(12);
        tvSummaryBox.setTextColor(0xFF444444);
        tvSummaryBox.setBackgroundColor(0xFFFFFFFF);
        int boxPad = (int) (8 * getResources().getDisplayMetrics().density);
        tvSummaryBox.setPadding(boxPad, boxPad, boxPad, boxPad);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.bottomMargin = 6;
        detailsContent.addView(tvSummaryBox, summaryParams);
        updateSummaryBox();

        LinearLayout reportButtonsLayout = new LinearLayout(this);
        reportButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        reportButtonsLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rbParams.bottomMargin = 6;
        detailsContent.addView(reportButtonsLayout, rbParams);

        btnCopyReport = new Button(this);
        btnCopyReport.setText("העתק דוח");
        btnCopyReport.setTextSize(11);
        btnCopyReport.setTextColor(0xFFFFFFFF);
        btnCopyReport.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams btnR1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnR1.rightMargin = 4;
        reportButtonsLayout.addView(btnCopyReport, btnR1);

        btnSaveReport = new Button(this);
        btnSaveReport.setText("שמור דוח");
        btnSaveReport.setTextSize(11);
        btnSaveReport.setTextColor(0xFFFFFFFF);
        btnSaveReport.setBackgroundColor(0xFF00838F);
        LinearLayout.LayoutParams btnR2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnR2.rightMargin = 4;
        reportButtonsLayout.addView(btnSaveReport, btnR2);

        btnDeepScanNow = new Button(this);
        btnDeepScanNow.setText("סריקה עמוקה");
        btnDeepScanNow.setTextSize(11);
        btnDeepScanNow.setTextColor(0xFFFFFFFF);
        btnDeepScanNow.setBackgroundColor(0xFF6A1B9A);
        LinearLayout.LayoutParams btnR3 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnR3.rightMargin = 4;
        reportButtonsLayout.addView(btnDeepScanNow, btnR3);

        btnTestRestore = new Button(this);
        btnTestRestore.setText("Test restore");
        btnTestRestore.setTextSize(11);
        btnTestRestore.setTextColor(0xFFFFFFFF);
        btnTestRestore.setBackgroundColor(0xFFE65100);
        reportButtonsLayout.addView(btnTestRestore, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnCopyReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    final String reportText = DiagnosticReporter.generateFullReport(ScanActivity.this);
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Scan Report", reportText);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(ScanActivity.this, "הדוח המלא הועתק ללוח בהצלחה", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            }
        });

        btnSaveReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "sd_rescue_report.txt");
                try {
                    startActivityForResult(intent, REQUEST_CODE_SAVE_REPORT);
                } catch (Exception ignored) {}
            }
        });

        btnDeepScanNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isDeepScanning) {
                    Toast.makeText(ScanActivity.this, "הסריקה העמוקה כבר רצה...", Toast.LENGTH_SHORT).show();
                    return;
                }
                runManualDeepScan();
            }
        });

        btnTestRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForSaveDirectory(false, true);
            }
        });

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (6 * getResources().getDisplayMetrics().density)
        );
        pbParams.bottomMargin = 6;
        detailsContent.addView(progressBar, pbParams);

        LinearLayout topButtons = new LinearLayout(this);
        topButtons.setOrientation(LinearLayout.HORIZONTAL);
        detailsContent.addView(topButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnCancel = new Button(this);
        btnCancel.setText(R.string.cancel_scan);
        btnCancel.setTextSize(11);
        btnCancel.setTextColor(0xFFD32F2F);
        btnCancel.setBackgroundColor(0xFFFFEBEE);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.rightMargin = 4;
        topButtons.addView(btnCancel, btnParams);

        btnPauseResume = new Button(this);
        btnPauseResume.setText(R.string.pause_scan);
        btnPauseResume.setTextSize(11);
        btnPauseResume.setTextColor(0xFFE65100);
        btnPauseResume.setBackgroundColor(0xFFFFF3E0);
        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams2.rightMargin = 4;
        topButtons.addView(btnPauseResume, btnParams2);

        btnErrorLog = new Button(this);
        btnErrorLog.setText(R.string.view_error_log);
        btnErrorLog.setTextSize(11);
        btnErrorLog.setTextColor(0xFF455A64);
        btnErrorLog.setBackgroundColor(0xFFECEFF1);
        topButtons.addView(btnErrorLog, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            }
        });

        btnPauseResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isPaused = !isPaused;
                Intent intent = new Intent(ScanActivity.this, ScanService.class);
                intent.setAction(isPaused ? ScanService.ACTION_PAUSE : ScanService.ACTION_RESUME);
                startService(intent);
                btnPauseResume.setText(isPaused ? R.string.resume_scan : R.string.pause_scan);
            }
        });

        btnErrorLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDiagnosticReportDialog();
            }
        });

        listView.addHeaderView(headerContainer);
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
        tvRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSearching = false;
                etSearch.setText("");
                currentDirPath = "/";
                currentParentId = dbHelper.getRootId();
                updateBreadcrumbBar();
                reloadCurrentDirectoryFromDb();
            }
        });
        layoutBreadcrumb.addView(tvRoot);

        if (currentDirPath == null || currentDirPath.equals("/")) return;

        String[] parts = currentDirPath.split("/");
        String pathSoFar = "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            pathSoFar += "/" + part;
            final String targetPath = pathSoFar;
            final long targetId = dbHelper.getNodeId(targetPath.substring(0, targetPath.lastIndexOf('/') > 0 ? targetPath.lastIndexOf('/') : 0), part);

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
            tvPart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isSearching = false;
                    etSearch.setText("");
                    currentDirPath = targetPath;
                    long foundId = dbHelper.getNodeId(targetPath.substring(0, targetPath.lastIndexOf('/')), part);
                    if (foundId != -1) {
                        currentParentId = foundId;
                    }
                    updateBreadcrumbBar();
                    reloadCurrentDirectoryFromDb();
                }
            });
            layoutBreadcrumb.addView(tvPart);
        }
    }

    private void performSearch(final String query) {
        if (query == null || query.trim().isEmpty()) {
            isSearching = false;
            reloadCurrentDirectoryFromDb();
            return;
        }

        isSearching = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<RecoveredEntry> results = dbHelper.searchNodes(query);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        searchResults = results;
                        currentDirEntries = searchResults;
                        if (tvFolderStatsLine != null) {
                            tvFolderStatsLine.setText("תוצאות חיפוש עבור \"" + query + "\": נמצאו " + searchResults.size() + " פריטים");
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void updateSelectionCounter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long[] stats = dbHelper.getSelectedStats();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        long files = stats[0];
                        long bytes = stats[1];
                        if (tvSelectionCounter != null) {
                            tvSelectionCounter.setText(String.format(Locale.getDefault(), "נבחרו: %d קבצים (%s)", files, formatSize(bytes)));
                        }
                    }
                });
            }
        }).start();
    }

    private void reloadCurrentDirectoryFromDb() {
        if (isSearching) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<RecoveredEntry> list = dbHelper.getChildrenOfDirectory(currentParentId);
                final int foldersCount = dbHelper.getChildrenFoldersCountForNode(currentParentId);
                final int filesCount = dbHelper.getChildrenFilesCountForNode(currentParentId);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        currentDirEntries = list;
                        updateBreadcrumbBar();
                        updateSummaryBox();
                        if (tvFolderStatsLine != null) {
                            tvFolderStatsLine.setText(String.format(Locale.getDefault(),
                                    "In this folder (DB): %d folders, %d files, shown: %d",
                                    foldersCount, filesCount, currentDirEntries.size()));
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void updateSummaryBox() {
        updateSummaryBoxWithCounts(lastBroadcastFiles, lastBroadcastFolders);
    }

    private void updateSummaryBoxWithCounts(int files, int folders) {
        int totalNodes = files + folders;
        int orphans = dbHelper != null ? dbHelper.getOrphansCount() : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("סיכום סריקה:\n");
        sb.append("• מערכת קבצים: ").append(FilesystemParser.getRecoveryMethodUsed()).append("\n");
        sb.append("• סך קבצים: ").append(files).append("\n");
        sb.append("• סך תיקיות: ").append(folders).append("\n");
        sb.append("nodes: ").append(totalNodes).append(", orphans: ").append(orphans);
        if (tvSummaryBox != null) {
            tvSummaryBox.setText(sb.toString());
        }
    }

    @Override
    public void onBackPressed() {
        if (isSearching) {
            isSearching = false;
            etSearch.setText("");
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
                currentParentId = dbHelper.getRootId();
            } else {
                parentPath = currentDirPath.substring(0, lastSlash);
                folderName = currentDirPath.substring(lastSlash + 1);
                currentDirPath = parentPath;
                long pid = dbHelper.getParentId(parentPath, folderName);
                if (pid != -1) {
                    currentParentId = pid;
                } else {
                    currentParentId = dbHelper.getRootId();
                }
            }
            updateBreadcrumbBar();
            reloadCurrentDirectoryFromDb();
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
        if (!isSearching) reloadCurrentDirectoryFromDb();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(scanReceiver);
            unregisterReceiver(restoreReceiver);
        } catch (Exception ignored) {}
    }

    private void promptForSaveDirectory(boolean saveAll, boolean isTest) {
        this.saveAllModeFlag = saveAll;
        this.isTestRestoreFlag = isTest;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String savedUriStr = prefs.getString(PREF_DEST_URI, null);
        if (savedUriStr != null) {
            try {
                Uri initialUri = Uri.parse(savedUriStr);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

                // Compute details for confirmation dialog
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long count = 0;
                        long bytes = 0;
                        if (isTestRestoreFlag) {
                            count = 1;
                            bytes = 4096;
                        } else if (saveAllModeFlag) {
                            List<RecoveredEntry> allFiles = dbHelper.getAllEntriesForSaveAll();
                            for (RecoveredEntry e : allFiles) {
                                if (!e.isDirectory()) {
                                    count++;
                                    bytes += e.getLength();
                                }
                            }
                        } else {
                            long[] stats = dbHelper.getSelectedStats();
                            count = stats[0];
                            bytes = stats[1];
                        }

                        final long finalCount = count;
                        final long finalBytes = bytes;
                        final String readablePath = getReadablePathFromTreeUri(treeUri);

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                showRestoreConfirmationDialog(treeUri, finalCount, finalBytes, readablePath);
                            }
                        });
                    }
                }).start();
            }
        } else if (requestCode == REQUEST_CODE_SAVE_REPORT && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                writeReportToFile(data.getData());
            }
        }
    }

    private String getReadablePathFromTreeUri(Uri treeUri) {
        if (treeUri == null) return "Unknown";
        String path = treeUri.getPath();
        if (path != null) {
            if (path.contains("tree/")) {
                String sub = path.substring(path.indexOf("tree/") + 5);
                sub = sub.replace("%3A", ":").replace("%2F", "/");
                return sub;
            }
        }
        return treeUri.toString();
    }

    private void showRestoreConfirmationDialog(final Uri treeUri, long fileCount, long totalBytes, String readablePath) {
        String message = String.format(Locale.getDefault(),
                "Restore %d files (%s) to:\n%s",
                fileCount, formatSize(totalBytes), readablePath);

        new AlertDialog.Builder(this)
                .setTitle("אישור שחזור")
                .setMessage(message)
                .setPositiveButton("Start", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isTestRestoreFlag) {
                            runTestRestore(treeUri);
                        } else {
                            startRestoreService(treeUri, saveAllModeFlag);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void writeReportToFile(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out != null) {
                String report = DiagnosticReporter.generateFullReport(this);
                out.write(report.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "הדוח נשמר בהצלחה כקובץ טקסט", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {}
    }

    private void startRestoreService(Uri treeUri, boolean saveAll) {
        Toast.makeText(this, "מתחיל שחזור קבצים ברקע...", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, RestoreService.class);
        intent.putExtra(RestoreService.EXTRA_TREE_URI, treeUri);
        intent.putExtra(RestoreService.EXTRA_SAVE_ALL, saveAll);
        if (sourceUri != null) {
            intent.putExtra(RestoreService.EXTRA_URI, sourceUri);
        }
        if (usbDeviceName != null) {
            intent.putExtra(RestoreService.EXTRA_USB_DEVICE_NAME, usbDeviceName);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void runTestRestore(final Uri treeUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DocumentFileCompat rootDir = DocumentFileCompat.fromTreeUri(ScanActivity.this, treeUri);
                    if (rootDir == null || !rootDir.canWrite()) {
                        throw new Exception("Destination root directory is null or not writable.");
                    }

                    List<RecoveredEntry> allFiles = dbHelper.getAllEntriesForSaveAll();
                    RecoveredEntry testEntry = null;
                    for (RecoveredEntry e : allFiles) {
                        if (!e.isDirectory() && e.getLength() > 0) {
                            testEntry = e;
                            break;
                        }
                    }

                    if (testEntry == null) {
                        throw new Exception("No files found in database to perform test restore.");
                    }

                    String testFileName = "test_restore_" + sanitizeFileName(testEntry.getName());
                    String mimeType = getMimeTypeForFile(testFileName, testEntry.getType());

                    int sampleLen = (int) Math.min(testEntry.getLength(), 4096L);
                    byte[] cardBuffer = new byte[sampleLen];
                    int cardRead = byteSource.readAt(testEntry.getOffset(), cardBuffer, sampleLen);
                    if (cardRead <= 0) {
                        throw new Exception("Failed to read test sample from source card.");
                    }

                    DocumentFileCompat testFile = rootDir.createFile(mimeType, testFileName);
                    if (testFile == null) {
                        throw new Exception("Failed to create test file in destination tree.");
                    }

                    long restoredSize = 0;
                    try (OutputStream out = getContentResolver().openOutputStream(testFile.getUri())) {
                        if (out == null) {
                            throw new Exception("Could not open OutputStream for test file URI.");
                        }
                        long remaining = testEntry.getLength();
                        long offset = testEntry.getOffset();
                        byte[] buf = new byte[16384];
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buf.length, remaining);
                            int read = byteSource.readAt(offset, buf, toRead);
                            if (read <= 0) break;
                            out.write(buf, 0, read);
                            offset += read;
                            remaining -= read;
                            restoredSize += read;
                        }
                        out.flush();
                    }

                    byte[] destBuffer = new byte[sampleLen];
                    try (InputStream in = getContentResolver().openInputStream(testFile.getUri())) {
                        if (in != null) {
                            in.read(destBuffer, 0, sampleLen);
                        }
                    }

                    boolean matches = true;
                    for (int i = 0; i < sampleLen; i++) {
                        if (cardBuffer[i] != destBuffer[i]) {
                            matches = false;
                            break;
                        }
                    }

                    StringBuilder info = new StringBuilder();
                    info.append("Restored size: ").append(restoredSize).append(" bytes\n");
                    info.append("Expected size: ").append(testEntry.getLength()).append(" bytes\n");
                    info.append("Comparison 4096 bytes: ").append(matches ? "OK" : "NOT OK").append("\n");

                    final String resultMessage = info.toString();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(ScanActivity.this)
                                    .setTitle("Test Restore Result")
                                    .setMessage(resultMessage)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    });

                } catch (final Exception e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(ScanActivity.this)
                                    .setTitle("Test Restore Failed")
                                    .setMessage("Exception:\n" + e.getMessage())
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                    });
                }
            }
        }).start();
    }

    private String getMimeTypeForFile(String fileName, FileType type) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return FileType.safeMimeType(type);
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[/\\\\?%*:|<>\"]", "_");
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

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("דוח שחזור קבצים")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void runManualDeepScan() {
        if (byteSource == null) {
            Toast.makeText(this, "אין מקור סריקה זמין", Toast.LENGTH_SHORT).show();
            return;
        }

        isDeepScanning = true;
        Toast.makeText(this, "מתחיל סריקה עמוקה (Carving)...", Toast.LENGTH_SHORT).show();
        tvStatus.setText("מבצע סריקה עמוקה (Carving)...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Carver carver = new Carver();
                    carver.carve(byteSource, new CarverCallback() {
                        @Override
                        public void onFileFound(final CarvedFile carvedFile) {
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
                                    fileName.getBytes(StandardCharsets.UTF_8),
                                    0
                            );
                            List<RecoveredEntry> single = new ArrayList<>();
                            single.add(entry);
                            dbHelper.insertEntriesInBatch(single);
                            if (!isSearching) reloadCurrentDirectoryFromDb();
                        }

                        @Override
                        public void onProgress(final long bytesScanned, final long totalBytes) {
                            final int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                            new Handler(Looper.getMainLocat().getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    tvStatus.setText("סריקה עמוקה... (" + percent + "%)");
                                }
                            });
                        }
                    });

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            isDeepScanning = false;
                            tvStatus.setText("הסריקה העמוקה הסתיימה בהצלחה");
                            if (!isSearching) reloadCurrentDirectoryFromDb();
                            checkOrphansAndWarn();
                        }
                    });
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            isDeepScanning = false;
                            Toast.makeText(ScanActivity.this, "שגיאה בסריקה העמוקה", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showDiagnosticReportDialog() {
        String report = DiagnosticReporter.generateFullReport(this);

        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(12);
        tv.setTextColor(0xFF333333);
        tv.setTextIsSelectable(true);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle(R.string.error_log_title)
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
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            ViewHolder holder;

            if (convertView == null) {
                itemLayout = new LinearLayout(ScanActivity.this);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                itemLayout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                itemLayout.setPadding(pad, pad, pad, pad);
                itemLayout.setBackgroundColor(0xFFFFFFFF);

                CheckBox checkBox = new CheckBox(ScanActivity.this);
                checkBox.setFocusable(false);
                checkBox.setClickable(true);
                itemLayout.addView(checkBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                ImageView ivIcon = new ImageView(ScanActivity.this);
                int iconSize = (int) (36 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(iconSize, iconSize);
                imgParams.leftMargin = pad;
                imgParams.rightMargin = pad;
                ivIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemLayout.addView(ivIcon, imgParams);

                LinearLayout textLayout = new LinearLayout(ScanActivity.this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.addView(textLayout, new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                ));

                TextView tvName = new TextView(ScanActivity.this);
                tvName.setTextSize(15);
                tvName.setTextColor(0xFF333333);
                tvName.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                textLayout.addView(tvName);

                TextView tvDetails = new TextView(ScanActivity.this);
                tvDetails.setTextSize(13);
                tvDetails.setTextColor(0xFF757575);
                tvDetails.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                textLayout.addView(tvDetails);

                Button btnDetails = new Button(ScanActivity.this);
                btnDetails.setText("פרטים");
                btnDetails.setTextSize(11);
                btnDetails.setTextColor(0xFF1976D2);
                btnDetails.setFocusable(false);
                itemLayout.addView(btnDetails, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                holder = new ViewHolder();
                holder.checkBox = checkBox;
                holder.ivIcon = ivIcon;
                holder.tvName = tvName;
                holder.tvDetails = tvDetails;
                holder.btnDetails = btnDetails;
                itemLayout.setTag(holder);
            } else {
                itemLayout = (LinearLayout) convertView;
                holder = (ViewHolder) itemLayout.getTag();
            }

            final RecoveredEntry entry = (RecoveredEntry) getItem(position);
            if (entry == null) return itemLayout;

            holder.checkBox.setChecked(entry.isSelected());
            if (entry.isPartial()) {
                holder.checkBox.jumpDrawablesToCurrentState();
            }

            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean newState = holder.checkBox.isChecked();
                    int targetState = newState ? 1 : 0;
                    dbHelper.setNodeAndSubtreeSelected(entry, targetState);
                    updateSelectionCounter();
                    notifyDataSetChanged();
                }
            });

            if (entry.isDirectory()) {
                if (entry.isUnreadable()) {
                    holder.ivIcon.setImageResource(android.R.drawable.ic_delete);
                    holder.tvName.setText("⚠️ " + entry.getName() + " (לא קריא)");
                    holder.tvName.setTextColor(0xFFE65100);
                    String folderDesc = String.format(Locale.getDefault(), "%d קבצים • %s (לא קריא)", entry.getCachedRecursiveFileCount(), formatSize(entry.getCachedRecursiveSize()));
                    holder.tvDetails.setText(folderDesc);
                } else {
                    holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
                    holder.tvName.setText("📁 " + entry.getName());
                    holder.tvName.setTextColor(0xFF333333);
                    String folderDesc = String.format(Locale.getDefault(), "%d קבצים • %s", entry.getCachedRecursiveFileCount(), formatSize(entry.getCachedRecursiveSize()));
                    holder.tvDetails.setText(folderDesc);
                }
                holder.btnDetails.setVisibility(View.GONE);
            } else {
                holder.tvName.setTextColor(0xFF333333);
                if (isSearching) {
                    holder.tvName.setText(entry.getName());
                    holder.tvDetails.setText("נתיב: " + entry.getPath() + " • " + formatSize(entry.getLength()));
                } else {
                    holder.tvName.setText(entry.getName());
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(entry.getModifiedTime()));
                    holder.tvDetails.setText("גודל: " + formatSize(entry.getLength()) + " • " + dateStr);
                }
                holder.btnDetails.setVisibility(View.VISIBLE);

                holder.ivIcon.setImageBitmap(null);
                if (entry.getType() == FileType.JPEG || entry.getType() == FileType.PNG) {
                    if (byteSource != null) {
                        try {
                            byte[] thumbBuf = new byte[(int) Math.min(entry.getLength(), 16384)];
                            int read = byteSource.readAt(entry.getOffset(), thumbBuf, thumbBuf.length);
                            if (read > 0) {
                                Bitmap bmp = BitmapFactory.decodeByteArray(thumbBuf, 0, read);
                                if (bmp != null) holder.ivIcon.setImageBitmap(bmp);
                            }
                        } catch (Exception ignored) {}
                    }
                } else {
                    holder.ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                }

                holder.btnDetails.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRawBytesHexDialog(entry);
                    }
                });
            }

            itemLevelClickSetup(itemLayout, entry, holder);

            return itemLayout;
        }

        private void itemLevelClickSetup(View itemLayout, final RecoveredEntry entry, ViewHolder holder) {
            itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (entry.isDirectory()) {
                        isSearching = false;
                        etSearch.setText("");
                        currentParentId = entry.getId();
                        currentDirPath = currentDirPath.equals("/") ? "/" + entry.getName() : currentDirPath + "/" + entry.getName();
                        updateBreadcrumbBar();
                        reloadCurrentDirectoryFromDb();
                    } else {
                        showRawBytesHexDialog(entry);
                    }
                }
            });

            itemLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showRawBytesHexDialog(entry);
                    return true;
                }
            });
        }

        class ViewHolder {
            CheckBox checkBox;
            ImageView ivIcon;
            TextView tvName;
            TextView tvDetails;
            Button btnDetails;
        }
    }

    private void showRawBytesHexDialog(RecoveredEntry entry) {
        int childrenParentIdCount = dbHelper.getChildrenCountForNode(entry.getId());
        int childrenFoldersCount = dbHelper.getChildrenFoldersCountForNode(entry.getId());
        int childrenFilesCount = dbHelper.getChildrenFilesCountForNode(entry.getId());
        int orphansCount = dbHelper.getOrphansCount();

        StringBuilder sb = new StringBuilder();
        sb.append("=== אבחון פרטי צומת וקישורי תיקיות (Folder Links Diagnostics) ===\n");
        sb.append("• Node ID: ").append(entry.getId()).append("\n");
        sb.append("• Parent ID: ").append(entry.getParentId()).append("\n");
        sb.append("• First Cluster: ").append(entry.getFirstCluster()).append("\n");
        sb.append("• Stored Offset: 0x").append(Long.toHexString(entry.getOffset())).append("\n\n");

        if (entry.isDirectory()) {
            sb.append("• Unreadable Flag: ").append(entry.isUnreadable() ? "YES (1)" : "NO (0)").append("\n");
            sb.append("• Unreadable Reason: ").append(entry.getUnreadableReason() != null ? entry.getUnreadableReason() : "None").append("\n\n");
        }

        sb.append("• Children count (parent_id = node id): ").append(childrenParentIdCount)
                .append(" (Folders: ").append(childrenFoldersCount).append(", Files: ").append(childrenFilesCount).append(")\n");
        sb.append("• Orphans in DB: ").append(orphansCount).append("\n\n");

        sb.append("שם הקובץ: ").append(entry.getName()).append("\n");
        sb.append("נתיב: ").append(entry.getPath()).append("\n");
        sb.append("גודל: ").append(entry.getLength()).append(" בייטס\n");
        sb.append("סוג: ").append(FileType.safeName(entry.getType())).append("\n\n");
        sb.append("רשומות ספריה גולמיים (כולל LFN במידת הצורך) בתצוגת Hex:\n");
        
        byte[] bytes = entry.getRawNameBytes();
        if (bytes != null && bytes.length > 0) {
            for (int i = 0; i < bytes.length; i++) {
                sb.append(String.format("%02X ", bytes[i]));
                if ((i + 1) % 16 == 0) sb.append("\n");
            }
        } else {
            sb.append("אין בתים גולמיים זמינים.");
        }

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
                .setTitle("פרטי קובץ גולמיים ומתקדמים (Name details)")
                .setView(sv)
                .setPositiveButton("העתק פרטים", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("File Details", sb.toString());
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(ScanActivity.this, "הפרטים הועתקו ללוח", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 בייט";
        final String[] units = new String[]{"בייט", "קילובייט", "מגהבייט", "ג׳יגהבייט", "טרהבייט"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format(java.util.Locale.getDefault(), "%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (byteSource != null) {
            byteSource.close();
        }
    }
}
