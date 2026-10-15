package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryFilesActivity extends Activity {

    public static final String EXTRA_CATEGORY_KEY = "extra_category_key";
    public static final String EXTRA_CATEGORY_NAME = "extra_category_name";
    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";

    private static final int REQUEST_CODE_SAVE_DIR = 4004;
    private static final String PREF_DEST_URI = "pref_dest_uri";

    private String categoryKey;
    private String categoryDisplayName;
    private Uri sourceUri;
    private String usbDeviceName;
    private DatabaseHelper dbHelper;

    private EditText etSearch;
    private EditText etMinSizeKb;
    private Button btnSelectAll;
    private Button btnClearSelection;
    private TextView tvSelectionCounter;
    private ListView listView;
    private ProgressBar loadingProgress;
    private Button btnRestoreCategory;

    private List<RecoveredEntry> filteredEntries = new ArrayList<>();
    private CategoryFilesAdapter adapter;

    private boolean keepOriginalFolders = true;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        dbHelper = DatabaseHelper.getInstance(this);
        categoryKey = getIntent().getStringExtra(EXTRA_CATEGORY_KEY);
        categoryDisplayName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        sourceUri = getIntent().getParcelableExtra(EXTRA_URI);
        usbDeviceName = getIntent().getStringExtra(EXTRA_USB_DEVICE_NAME);

        if (categoryKey == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFF8F9FA);

        int pad = (int) (12 * getResources().getDisplayMetrics().density);

        // Content wrapper with height 0 and weight 1 so RestoreProgressBarView stays visible at the bottom
        LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setPadding(pad, pad, pad, pad);
        topBar.setBackgroundColor(0xFFFFFFFF);
        contentContainer.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(categoryDisplayName != null ? categoryDisplayName : "קבצים בקטגוריה");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = 8;
        topBar.addView(tvTitle, titleParams);

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
        topBar.addView(etSearch, searchParams);

        LinearLayout filterLayout = new LinearLayout(this);
        filterLayout.setOrientation(LinearLayout.HORIZONTAL);
        filterLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams flParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        flParams.bottomMargin = 8;
        topBar.addView(filterLayout, flParams);

        TextView tvFilterLabel = new TextView(this);
        tvFilterLabel.setText("הסתר קבצים קטנים מ- (KB): ");
        tvFilterLabel.setTextSize(13);
        tvFilterLabel.setTextColor(0xFF333333);
        filterLayout.addView(tvFilterLabel);

        etMinSizeKb = new EditText(this);
        etMinSizeKb.setTextSize(14);
        etMinSizeKb.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (FileCategory.PHOTOS.equals(categoryKey)) {
            etMinSizeKb.setText("10");
        } else {
            etMinSizeKb.setText("0");
        }
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(
                (int) (80 * getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        filterLayout.addView(etMinSizeKb, sizeParams);

        LinearLayout selActionLayout = new LinearLayout(this);
        selActionLayout.setOrientation(LinearLayout.HORIZONTAL);
        selActionLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        selParams.bottomMargin = 4;
        topBar.addView(selActionLayout, selParams);

        btnSelectAll = new Button(this);
        btnSelectAll.setText("בחר הכל");
        btnSelectAll.setTextSize(12);
        btnSelectAll.setTextColor(0xFFFFFFFF);
        btnSelectAll.setBackgroundColor(0xFF1976D2);
        LinearLayout.LayoutParams btnSelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnSelLp.rightMargin = 4;
        selActionLayout.addView(btnSelectAll, btnSelLp);

        btnClearSelection = new Button(this);
        btnClearSelection.setText("בטל בחירה");
        btnClearSelection.setTextSize(12);
        btnClearSelection.setTextColor(0xFF333333);
        btnClearSelection.setBackgroundColor(0xFFE0E0E0);
        selActionLayout.addView(btnClearSelection, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        tvSelectionCounter = new TextView(this);
        tvSelectionCounter.setTextSize(13);
        tvSelectionCounter.setTextColor(0xFF1976D2);
        tvSelectionCounter.setTypeface(null, android.graphics.Typeface.BOLD);
        topBar.addView(tvSelectionCounter, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout listContainer = new FrameLayout(this);
        contentContainer.addView(listContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        listView = new ListView(this);
        listContainer.addView(listView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        loadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        FrameLayout.LayoutParams progLp = new FrameLayout.LayoutParams(
                (int) (48 * getResources().getDisplayMetrics().density),
                (int) (48 * getResources().getDisplayMetrics().density),
                Gravity.CENTER
        );
        listContainer.addView(loadingProgress, progLp);
        loadingProgress.setVisibility(View.GONE);

        adapter = new CategoryFilesAdapter();
        listView.setAdapter(adapter);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(pad, pad, pad, pad);
        bottomBar.setBackgroundColor(0xFFFFFFFF);
        contentContainer.addView(bottomBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnRestoreCategory = new Button(this);
        btnRestoreCategory.setText("שחזר נבחרים בקטגוריה");
        btnRestoreCategory.setTextColor(0xFFFFFFFF);
        btnRestoreCategory.setBackgroundColor(0xFF388E3C);
        bottomBar.addView(btnRestoreCategory, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Add RestoreProgressBarView pinned at the bottom of the root layout
        RestoreProgressBarView restoreProgressBarView = new RestoreProgressBarView(this);
        root.addView(restoreProgressBarView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextWatcher filterWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> loadEntries();
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etSearch.addTextChangedListener(filterWatcher);
        etMinSizeKb.addTextChangedListener(filterWatcher);

        btnSelectAll.setOnClickListener(v -> {
            for (RecoveredEntry e : filteredEntries) {
                e.setSelectedState(1);
            }
            updateSelectionCounter();
            adapter.notifyDataSetChanged();
        });

        btnClearSelection.setOnClickListener(v -> {
            for (RecoveredEntry e : filteredEntries) {
                e.setSelectedState(0);
            }
            updateSelectionCounter();
            adapter.notifyDataSetChanged();
        });

        btnRestoreCategory.setOnClickListener(v -> promptRestoreOptions());

        setContentView(root);
        loadEntries();
    }

    private void loadEntries() {
        runOnUiThread(() -> {
            if (loadingProgress != null) loadingProgress.setVisibility(View.VISIBLE);
        });

        new Thread(() -> {
            long minSizeKb = 0;
            try {
                minSizeKb = Long.parseLong(etMinSizeKb.getText().toString().trim());
            } catch (Exception ignored) {}
            long minSizeBytes = minSizeKb * 1024;

            List<RecoveredEntry> matched = dbHelper.getEntriesByCategory(categoryKey, minSizeBytes);
            String query = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

            List<RecoveredEntry> finalFiltered = new ArrayList<>();
            for (RecoveredEntry e : matched) {
                if (!query.isEmpty() && !e.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
                finalFiltered.add(e);
            }

            filteredEntries = finalFiltered;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                updateSelectionCounter();
            });
        }).start();
    }

    private void updateSelectionCounter() {
        long count = 0;
        long totalBytes = 0;
        for (RecoveredEntry e : filteredEntries) {
            if (e.isSelected()) {
                count++;
                totalBytes += e.getLength();
            }
        }
        tvSelectionCounter.setText(String.format(Locale.getDefault(), "נבחרו בקטגוריה: %d קבצים (%s)", count, formatSize(totalBytes)));
    }

    private void promptRestoreOptions() {
        long selectedCount = 0;
        for (RecoveredEntry e : filteredEntries) {
            if (e.isSelected()) selectedCount++;
        }
        if (selectedCount == 0) {
            Toast.makeText(this, "לא נבחרו קבצים לשחזור", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("אופן שמירת הקבצים")
                .setMessage("כיצד תרצה לשמור את הקבצים המשוחזרים?")
                .setPositiveButton("שמור בתיקיות המקוריות", (dialog, which) -> {
                    keepOriginalFolders = true;
                    openDirectoryPicker();
                })
                .setNegativeButton("שם הכל בתיקייה אחת", (dialog, which) -> {
                    keepOriginalFolders = false;
                    openDirectoryPicker();
                })
                .show();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String savedUriStr = prefs.getString(PREF_DEST_URI, null);
        if (savedUriStr != null) {
            try {
                Uri initialUri = Uri.parse(savedUriStr);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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

                executeCategoryRestore(treeUri);
            }
        }
    }

    private void executeCategoryRestore(final Uri treeUri) {
        final List<RecoveredEntry> filesToRestore = new ArrayList<>();
        for (RecoveredEntry e : filteredEntries) {
            if (e.isSelected()) {
                filesToRestore.add(e);
            }
        }

        if (filesToRestore.isEmpty()) return;

        Toast.makeText(this, "מתחיל שחזור קבצים מהקטגוריה...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            dbHelper.setAllSelected(false);
            for (RecoveredEntry e : filesToRestore) {
                dbHelper.setNodeAndSubtreeSelected(e, 1);
            }

            RestoreUtils.startRestoreService(CategoryFilesActivity.this, treeUri, false, sourceUri, usbDeviceName);

            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(CategoryFilesActivity.this, "השחזור החל ברקע עבור קבצי הקטגוריה", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private class CategoryFilesAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return filteredEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            RecoveredEntry entry = filteredEntries.get(position);
            return entry != null ? entry.getId() : position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            CheckBox checkBox;
            TextView tvName;
            TextView tvSubtitle;

            if (convertView == null) {
                itemLayout = new LinearLayout(CategoryFilesActivity.this);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (10 * getResources().getDisplayMetrics().density);
                itemLayout.setPadding(pad, pad, pad, pad);
                itemLayout.setBackgroundColor(0xFFFFFFFF);

                checkBox = new CheckBox(CategoryFilesActivity.this);
                checkBox.setFocusable(false);
                itemLayout.addView(checkBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                LinearLayout textLayout = new LinearLayout(CategoryFilesActivity.this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                );
                textLp.leftMargin = pad;
                itemLayout.addView(textLayout, textLp);

                tvName = new TextView(CategoryFilesActivity.this);
                tvName.setTextSize(15);
                tvName.setTextColor(0xFF333333);
                textLayout.addView(tvName);

                tvSubtitle = new TextView(CategoryFilesActivity.this);
                tvSubtitle.setTextSize(12);
                tvSubtitle.setTextColor(0xFF757575);
                textLayout.addView(tvSubtitle);

                itemLayout.setTag(new CategoryFileViewHolder(checkBox, tvName, tvSubtitle));
            } else {
                itemLayout = (LinearLayout) convertView;
                CategoryFileViewHolder holder = (CategoryFileViewHolder) itemLayout.getTag();
                checkBox = holder.checkBox;
                tvName = holder.tvName;
                tvSubtitle = holder.tvSubtitle;
            }

            final RecoveredEntry entry = filteredEntries.get(position);
            checkBox.setChecked(entry.isSelected());
            tvName.setText(entry.getName());
            tvSubtitle.setText(String.format(Locale.getDefault(), "נתיב: %s • גודל: %s", entry.getPath(), formatSize(entry.getLength())));

            checkBox.setOnClickListener(v -> {
                entry.setSelectedState(checkBox.isChecked() ? 1 : 0);
                updateSelectionCounter();
            });

            itemLayout.setOnClickListener(v -> {
                boolean newState = !entry.isSelected();
                entry.setSelectedState(newState ? 1 : 0);
                checkBox.setChecked(newState);
                updateSelectionCounter();
            });

            return itemLayout;
        }

        class CategoryFileViewHolder {
            final CheckBox checkBox;
            final TextView tvName;
            final TextView tvSubtitle;
            CategoryFileViewHolder(CheckBox checkBox, TextView tvName, TextView tvSubtitle) {
                this.checkBox = checkBox;
                this.tvName = tvName;
                this.tvSubtitle = tvSubtitle;
            }
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 בייט";
        final String[] units = new String[]{"בייט", "קילובייט", "מגהבייט", "ג׳יגהבייט", "טרהבייט"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
