package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.io.OutputStream;
import java.nio.charset.Charset;
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
    private Button btnTestRestore; // Item 4
    private ProgressBar progressBar;
    private Button btnCancel;
    private Button btnPauseResume;
    private Button btnToggleDetails;

    private String currentDirPath = "/";
    private List<RecoveredEntry> currentDirEntries = new ArrayList<>();
    private List<RecoveredEntry> searchResults = new ArrayList<>();
    private boolean isSearching = false;
    private TreeEntryAdapter adapter;

    private boolean isPaused = false;
    private boolean isDeepScanning = false;
    private boolean detailsExpanded = true;

    private int lastBroadcastFiles = 0;
    private int lastBroadcastFolders = 0;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private List<String> lastFailedErrors = new ArrayList<>();

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
                }
            } catch (Exception e) {
                DiagnosticReporter.logError(e, "Exception in ScanActivity BroadcastReceiver onReceive");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        dbHelper = DatabaseHelper.getInstance(this);
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

        if (!ScanService.isScanRunning()) {
            startScanService();
        } else {
            lastBroadcastFiles = ScanService.getFilesCount();
            lastBroadcastFolders = ScanService.getFoldersCount();
            tvStatus.setText("סורק... • נמצאו: " + (lastBroadcastFiles + lastBroadcastFolders));
            updateSummaryBoxWithCounts(lastBroadcastFiles, lastBroadcastFolders);
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
                    currentDirPath = currentDirPath.equals("/") ? "/" + clicked.getName() : currentDirPath + "/" + clicked.getName();
                    updateBreadcrumbBar();
                    reloadCurrentDirectoryFromDb();
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
                checkAndPromptDestination(false);
            }
        });

        btnSaveAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndPromptDestination(true);
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

        // Item 4: Test restore debug button
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
                    final String reportText = DiagnosticReporter.generateFullReport();
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Scan Report", reportText);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(ScanActivity.this, "הדוח המלא הועתק ללוח בהצלחה", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(ScanActivity.this, "שגיאה בהעתקת הדוח", Toast.LENGTH_SHORT).show();
                }
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
                } catch (Exception e) {
                    Toast.makeText(ScanActivity.this, "שגיאה בפתיחת שומר הקבצים", Toast.LENGTH_SHORT).show();
                }
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
                checkAndPromptDestinationForTest();
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
                Intent intent = new Intent(ScanActivity.this, ScanService.class);
                intent.setAction(ScanService.ACTION_CANCEL);
                startService(intent);
                finish();
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
                final List<RecoveredEntry> list = dbHelper.getChildrenOfDirectory(currentDirPath);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        currentDirEntries = list;
                        updateBreadcrumbBar();
                        updateSummaryBox();
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
        StringBuilder sb = new StringBuilder();
        sb.append("סיכום סריקה:\n");
        sb.append("• מערכת קבצים: ").append(FilesystemParser.getRecoveryMethodUsed()).append("\n");
        sb.append("• סך קבצים: ").append(files).append("\n");
        sb.append("• סך תיקיות: ").append(folders);
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
            if (lastSlash <= 0) {
                currentDirPath = "/";
            } else {
                currentDirPath = currentDirPath.substring(0, lastSlash);
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
        if (!isSearching) reloadCurrentDirectoryFromDb();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(scanReceiver);
        } catch (Exception ignored) {}
    }

    private void checkAndPromptDestination(boolean saveAll) {
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String savedUriStr = prefs.getString(PREF_DEST_URI, null);
        if (savedUriStr != null) {
            Uri destUri = Uri.parse(savedUriStr);
            try {
                getContentResolver().takePersistableUriPermission(destUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startRestoring(destUri, saveAll);
                return;
            } catch (Exception ignored) {}
        }
        promptForSaveDirectory(saveAll, false);
    }

    private void checkAndPromptDestinationForTest() {
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String savedUriStr = prefs.getString(PREF_DEST_URI, null);
        if (savedUriStr != null) {
            Uri destUri = Uri.parse(savedUriStr);
            try {
                getContentResolver().takePersistableUriPermission(destUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                runTestRestore(destUri);
                return;
            } catch (Exception ignored) {}
        }
        promptForSaveDirectory(false, true);
    }

    private boolean saveAllModeFlag = true;
    private boolean isTestRestoreFlag = false;

    private void promptForSaveDirectory(boolean saveAll, boolean isTest) {
        this.saveAllModeFlag = saveAll;
        this.isTestRestoreFlag = isTest;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(intent, REQUEST_CODE_SAVE_DIR);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_saving, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAVE_DIR && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri treeUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putString(PREF_DEST_URI, treeUri.toString()).apply();
                } catch (Exception ignored) {}

                if (isTestRestoreFlag) {
                    runTestRestore(treeUri);
                } else {
                    startRestoring(treeUri, saveAllModeFlag);
                }
            }
        } else if (requestCode == REQUEST_CODE_SAVE_REPORT && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                writeReportToFile(data.getData());
            }
        }
    }

    private void writeReportToFile(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out != null) {
                String report = DiagnosticReporter.generateFullReport();
                out.write(report.getBytes(Charset.forName("UTF-8")));
                Toast.makeText(this, "הדוח נשמר בהצלחה כקובץ טקסט", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשמירת הדוח לקובץ", Toast.LENGTH_SHORT).show();
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

                    DocumentFileCompat testFile = rootDir.createFile(mimeType, testFileName);
                    if (testFile == null) {
                        throw new Exception("Failed to create test file in destination tree.");
                    }

                    try (OutputStream out = getContentResolver().openOutputStream(testFile.getUri())) {
                        if (out == null) {
                            throw new Exception("Could not open OutputStream for test file URI.");
                        }
                        long remaining = Math.min(testEntry.getLength(), 65536L);
                        long offset = testEntry.getOffset();
                        byte[] buf = new byte[32768];
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buf.length, remaining);
                            int read = byteSource.readAt(offset, buf, toRead);
                            if (read <= 0) break;
                            out.write(buf, 0, read);
                            offset += read;
                            remaining -= read;
                        }
                        out.flush();
                    }

                    final String successMsg = "Test restore successful!\nRestored file: " + testFileName + " (" + testEntry.getLength() + " bytes)";
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(ScanActivity.this)
                                    .setTitle("Test Restore Result")
                                    .setMessage(successMsg)
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

    private void startRestoring(final Uri treeUri, final boolean saveAll) {
        Toast.makeText(this, "מתחיל שחזור קבצים ליעד שנבחר...", Toast.LENGTH_SHORT).show();

        Thread restoreThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                final List<String> errorLogs = new ArrayList<>();
                lastFailedErrors.clear();
                int[] successCount = new int[]{0};

                List<RecoveredEntry> entriesToRestore = saveAll ? dbHelper.getAllEntriesForSaveAll() : dbHelper.getAllSelectedFiles();
                
                DocumentFileCompat rootDir = DocumentFileCompat.fromTreeUri(ScanActivity.this, treeUri);
                if (rootDir == null || !rootDir.canWrite()) {
                    errorLogs.add("תקיית היעד אינה נגישה או שאין הרשאת כתיבה.");
                    showRestoreSummaryAndDialog(0, entriesToRestore.size(), errorLogs);
                    return;
                }

                for (RecoveredEntry entry : entriesToRestore) {
                    try {
                        saveNodeToDestinationUsingDocumentFile(resolver, rootDir, entry, successCount, errorLogs);
                    } catch (Exception e) {
                        String err = "שגיאת שחזור " + entry.getName() + ": " + e.getMessage();
                        errorLogs.add(err);
                        lastFailedErrors.add(entry.getName());
                    }
                }

                final int finalSuccess = successCount[0];
                final int finalFailed = errorLogs.size();

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        showRestoreSummaryAndDialog(finalSuccess, finalFailed, errorLogs);
                    }
                });
            }
        });
        restoreThread.start();
    }

    private void saveNodeToDestinationUsingDocumentFile(ContentResolver resolver, DocumentFileCompat rootDir, RecoveredEntry node, int[] successCount, List<String> errorLogs) {
        try {
            String path = node.getPath();
            DocumentFileCompat currentParent = rootDir;

            if (path != null && !path.equals("/")) {
                String[] segments = path.split("/");
                for (String seg : segments) {
                    if (seg.isEmpty()) continue;
                    DocumentFileCompat foundSub = currentParent.findFile(seg);
                    if (foundSub != null && foundSub.isDirectory()) {
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

                if (!uniqueName.equals(node.getName())) {
                    DiagnosticReporter.log("Note: Provider renamed created file from " + node.getName() + " to " + uniqueName);
                }

                try (OutputStream out = resolver.openOutputStream(targetFile.getUri())) {
                    if (out == null) {
                        throw new Exception("Could not open OutputStream for target URI.");
                    }
                    long remaining = node.getLength();
                    long offset = node.getOffset();
                    byte[] buf = new byte[262144]; // Item 1: 256 KB chunks
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int read = byteSource.readAt(offset, buf, toRead);
                        if (read <= 0) break;
                        out.write(buf, 0, read);
                        offset += read;
                        remaining -= read;
                    }
                    out.flush();
                    successCount[0]++;
                }
            }
        } catch (Exception e) {
            errorLogs.add("נכשל שחזור " + node.getName() + ": " + e.getMessage());
            lastFailedErrors.add(node.getName());
            DiagnosticReporter.logError(e, "Restore file error for " + node.getName());
        }
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

    private void showRestoreSummaryAndDialog(int success, int failed, final List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("סיכום תהליך שחזור:\n\n");
        sb.append("• שחזור בהצלחה: ").append(success).append(" קבצים\n");
        sb.append("• נכשלו בשחזור: ").append(failed).append(" פריטים\n\n");

        if (!errors.isEmpty()) {
            sb.append("פירוט שגיאות נבחרות:\n");
            for (int i = 0; i < Math.min(15, errors.size()); i++) {
                sb.append("• ").append(errors.get(i)).append("\n");
            }
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("דוח שחזור קבצים")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null);

        if (failed > 0) {
            builder.setNegativeButton("נסה שנית קבצים שנכשלו", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    retryFailedFiles();
                }
            });
        }

        builder.show();
    }

    private void retryFailedFiles() {
        if (lastFailedErrors.isEmpty()) {
            Toast.makeText(this, "אין קבצים נכשולים לניסיון חוזר", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "מנסה לשחזר שנית קבצים שנכשלו...", Toast.LENGTH_SHORT).show();
        checkAndPromptDestination(false);
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
                            RecoveredEntry entry = new RecoveredEntry(
                                    fileName,
                                    "/Carved files/" + typeFolderName,
                                    carvedFile.getOffset(),
                                    carvedFile.getLength(),
                                    carvedFile.getType(),
                                    false,
                                    false,
                                    System.currentTimeMillis(),
                                    fileName.getBytes(StandardCharsets.UTF_8)
                            );
                            List<RecoveredEntry> single = new ArrayList<>();
                            single.add(entry);
                            dbHelper.insertEntriesInBatch(single);
                            if (!isSearching) reloadCurrentDirectoryFromDb();
                        }

                        @Override
                        public void onProgress(final long bytesScanned, final long totalBytes) {
                            final int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
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
        String report = DiagnosticReporter.generateFullReport();

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
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
                holder.tvName.setText("📁 " + entry.getName());
                String folderDesc = String.format(Locale.getDefault(), "%d קבצים • %s", entry.getCachedRecursiveFileCount(), formatSize(entry.getCachedRecursiveSize()));
                holder.tvDetails.setText(folderDesc);
                holder.btnDetails.setVisibility(View.GONE);
            } else {
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
                    try {
                        byte[] thumbBuf = new byte[(int) Math.min(entry.getLength(), 16384)];
                        int read = byteSource.readAt(entry.getOffset(), thumbBuf, thumbBuf.length);
                        if (read > 0) {
                            Bitmap bmp = BitmapFactory.decodeByteArray(thumbBuf, 0, read);
                            if (bmp != null) holder.ivIcon.setImageBitmap(bmp);
                        }
                    } catch (Exception ignored) {}
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

            itemLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showRawBytesHexDialog(entry);
                    return true;
                }
            });

            return itemLayout;
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
        StringBuilder sb = new StringBuilder();
        sb.append("שם הקובץ: ").append(entry.getName()).append("\n");
        sb.append("נתיב: ").append(entry.getPath()).append("\n");
        sb.append("אופסט: 0x").append(Long.toHexString(entry.getOffset())).append("\n");
        sb.append("גודל: ").append(entry.getLength()).append(" בייטס\n");
        sb.append("סוג: ").append(FileType.safeName(entry.getType())).append("\n\n");
        sb.append("רשומות ספריה גולמיות (32 בייטס / Hex):\n");
        
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
                .setTitle("פרטי קובץ גולמיים ומתקדמים")
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
