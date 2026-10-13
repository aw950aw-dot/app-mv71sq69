package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanActivity extends Activity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    private static final int REQUEST_CODE_SAVE_DIR = 2002;

    private Uri sourceUri;
    private String usbDeviceName;
    private ByteSource byteSource;

    private TextView tvStatus;
    private TextView tvMethodUsed;
    private TextView tvBreadcrumb;
    private ProgressBar progressBar;
    private ListView listView;
    private Button btnCancel;
    private Button btnPauseResume;
    private Button btnSaveAll;
    private Button btnSaveSelected;
    private Button btnErrorLog;

    private final List<RecoveredEntry> allFlatEntries = new ArrayList<>();
    private RecoveredEntry rootNode;
    private RecoveredEntry currentDirectory;
    private TreeEntryAdapter adapter;

    private boolean isPaused = false;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();

            if (ScanService.ACTION_PROGRESS.equals(action)) {
                int percent = intent.getIntExtra("percent", 0);
                int count = intent.getIntExtra("count", 0);
                progressBar.setProgress(percent * 10);
                tvStatus.setText("סורק קבצים... (" + percent + "%) • נמצאו: " + count);
                String method = intent.getStringExtra("method");
                if (method != null && !method.isEmpty()) {
                    tvMethodUsed.setText("שיטת שחזור: " + method);
                }
            } else if (ScanService.ACTION_FILE_FOUND.equals(action)) {
                String name = intent.getStringExtra("name");
                String path = intent.getStringExtra("path");
                long offset = intent.getLongExtra("offset", 0);
                long length = intent.getLongExtra("length", 0);
                String typeStr = intent.getStringExtra("type");
                boolean isDeleted = intent.getBooleanExtra("is_deleted", false);
                long modTime = intent.getLongExtra("mod_time", System.currentTimeMillis());

                FileType type = FileType.JPEG;
                try { type = FileType.valueOf(typeStr); } catch (Exception ignored) {}

                RecoveredEntry entry = new RecoveredEntry(name, path, offset, length, type, isDeleted, false, modTime, name.getBytes());
                allFlatEntries.add(entry);
                refreshTree();
            } else if (ScanService.ACTION_COMPLETED.equals(action)) {
                tvStatus.setText(R.string.scan_completed);
                btnCancel.setText(android.R.string.ok);
                btnPauseResume.setVisibility(View.GONE);
                tvMethodUsed.setText("שיטת שחזור: הושלם בהצלחה (" + FilesystemParser.getRecoveryMethodUsed() + ")");
                refreshTree();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

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
        startScanService();
    }

    private void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF8F9FA);

        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(pad, pad, pad, pad);
        topCard.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        topParams.bottomMargin = pad;
        root.addView(topCard, topParams);

        tvStatus = new TextView(this);
        tvStatus.setText(R.string.scanning_title);
        tvStatus.setTextSize(15);
        tvStatus.setTextColor(0xFF333333);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);
        topCard.addView(tvStatus, statusParams);

        tvMethodUsed = new TextView(this);
        tvMethodUsed.setText("שיטת שחזור: מנתח מערכת קבצים...");
        tvMethodUsed.setTextSize(13);
        tvMethodUsed.setTextColor(0xFF00838F);
        LinearLayout.LayoutParams methodParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        methodParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        topCard.addView(tvMethodUsed, methodParams);

        tvBreadcrumb = new TextView(this);
        tvBreadcrumb.setText("מיקום: /");
        tvBreadcrumb.setTextSize(14);
        tvBreadcrumb.setTextColor(0xFF1976D2);
        tvBreadcrumb.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams bcParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bcParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        topCard.addView(tvBreadcrumb, bcParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (8 * getResources().getDisplayMetrics().density)
        );
        pbParams.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        topCard.addView(progressBar, pbParams);

        LinearLayout topButtons = new LinearLayout(this);
        topButtons.setOrientation(LinearLayout.HORIZONTAL);
        topCard.addView(topButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnCancel = new Button(this);
        btnCancel.setText(R.string.cancel_scan);
        btnCancel.setTextColor(0xFFD32F2F);
        btnCancel.setBackgroundColor(0xFFFFEBEE);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        btnParams.rightMargin = (int) (6 * getResources().getDisplayMetrics().density);
        topButtons.addView(btnCancel, btnParams);

        btnPauseResume = new Button(this);
        btnPauseResume.setText(R.string.pause_scan);
        btnPauseResume.setTextColor(0xFFE65100);
        btnPauseResume.setBackgroundColor(0xFFFFF3E0);
        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        btnParams2.rightMargin = (int) (6 * getResources().getDisplayMetrics().density);
        topButtons.addView(btnPauseResume, btnParams2);

        btnErrorLog = new Button(this);
        btnErrorLog.setText(R.string.view_error_log);
        btnErrorLog.setTextColor(0xFF455A64);
        btnErrorLog.setBackgroundColor(0xFFECEFF1);
        topButtons.addView(btnErrorLog, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        listParams.bottomMargin = pad;
        root.addView(listView, listParams);

        adapter = new TreeEntryAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                RecoveredEntry clicked = (RecoveredEntry) adapter.getItem(position);
                if (clicked != null && clicked.isDirectory()) {
                    currentDirectory = clicked;
                    updateBreadcrumb();
                    adapter.notifyDataSetChanged();
                }
            }
        });

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
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
        saveParams.rightMargin = (int) (8 * getResources().getDisplayMetrics().density);
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
                showErrorLogDialog();
            }
        });

        btnSaveSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForSaveDirectory(false);
            }
        });

        btnSaveAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForSaveDirectory(true);
            }
        });

        setContentView(root);
    }

    private void refreshTree() {
        List<RecoveredEntry> roots = FilesystemParser.buildTree(allFlatEntries);
        if (!roots.isEmpty()) {
            rootNode = roots.get(0);
            if (currentDirectory == null) {
                currentDirectory = rootNode;
            } else {
                // Keep current directory if still exists in tree
                currentDirectory = findDirectoryByPath(rootNode, currentDirectory.getName());
                if (currentDirectory == null) currentDirectory = rootNode;
            }
        }
        updateBreadcrumb();
        adapter.notifyDataSetChanged();
    }

    private RecoveredEntry findDirectoryByPath(RecoveredEntry node, String name) {
        if (node.getName().equals(name)) return node;
        for (RecoveredEntry child : node.getChildren()) {
            if (child.isDirectory()) {
                if (child.getName().equals(name)) return child;
                RecoveredEntry found = findDirectoryByPath(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void updateBreadcrumb() {
        if (currentDirectory == null) {
            tvBreadcrumb.setText("מיקום: /");
        } else {
            tvBreadcrumb.setText("מיקום: " + (currentDirectory.getName().isEmpty() ? "/" : "/" + currentDirectory.getName()));
        }
    }

    @Override
    public void onBackPressed() {
        if (currentDirectory != null && currentDirectory.getParent() != null) {
            currentDirectory = currentDirectory.getParent();
            updateBreadcrumb();
            adapter.notifyDataSetChanged();
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

        IntentFilter filter = new IntentFilter();
        filter.addAction(ScanService.ACTION_PROGRESS);
        filter.addAction(ScanService.ACTION_FILE_FOUND);
        filter.addAction(ScanService.ACTION_COMPLETED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
    }

    private void showErrorLogDialog() {
        List<String> logs = UsbMassStorageSource.getDiagnosticLogs();
        StringBuilder sb = new StringBuilder();
        sb.append("שיטת שחזור פעילה: ").append(FilesystemParser.getRecoveryMethodUsed()).append("\n\n");
        for (String log : logs) {
            sb.append(log).append("\n");
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
                .setTitle(R.string.error_log_title)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean saveAllModeFlag = true;

    private void promptForSaveDirectory(boolean saveAll) {
        this.saveAllModeFlag = saveAll;
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
                saveEntriesRecursive(data.getData(), saveAllModeFlag);
            }
        }
    }

    private void saveEntriesRecursive(final Uri treeUri, final boolean saveAll) {
        Toast.makeText(this, "מתחיל שחזור קבצים...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                final List<String> errorLogs = new ArrayList<>();
                int[] successCount = new int[]{0};

                if (rootNode != null) {
                    saveNodeToTree(resolver, treeUri, rootNode, saveAll, successCount, errorLogs);
                }

                final int finalSuccess = successCount[0];
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder msg = new StringBuilder();
                        msg.append("הסתיים השחזור. הצלחה: ").append(finalSuccess);
                        if (!errorLogs.isEmpty()) {
                            msg.append(" (נכשלו: ").append(errorLogs.size()).append(")");
                        }
                        Toast.makeText(ScanActivity.this, msg.toString(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void saveNodeToTree(ContentResolver resolver, Uri parentDirUri, RecoveredEntry node, boolean saveAll, int[] successCount, List<String> errorLogs) {
        if (!saveAll && !node.isSelected() && !hasSelectedDescendant(node)) return;

        if (node.isDirectory()) {
            Uri currentFolderUri = parentDirUri;
            if (!node.getName().isEmpty()) {
                try {
                    String sanitizedDirName = sanitizeFileName(node.getName());
                    currentFolderUri = DocumentsContract.createDocument(resolver, parentDirUri, DocumentsContract.Document.MIME_TYPE_DIR, sanitizedDirName);
                } catch (Exception e) {
                    errorLogs.append("יצירת תיקייה נכשלה: " + node.getName());
                    return;
                }
            }
            if (currentFolderUri != null) {
                for (RecoveredEntry child : node.getChildren()) {
                    saveNodeToTree(resolver, currentFolderUri, child, saveAll, successCount, errorLogs);
                }
            }
        } else {
            if (saveAll || node.isSelected()) {
                try {
                    String sanitizedName = sanitizeFileName(node.getName());
                    Uri docUri = DocumentsContract.createDocument(resolver, parentDirUri, node.getType().getMimeType(), sanitizedName);
                    if (docUri != null) {
                        try (OutputStream out = resolver.openOutputStream(docUri)) {
                            if (out != null) {
                                long remaining = node.getLength();
                                long offset = node.getOffset();
                                byte[] buf = new byte[32768];
                                while (remaining > 0) {
                                    int toRead = (int) Math.min(buf.length, remaining);
                                    int read = byteSource.readAt(offset, buf, toRead);
                                    if (read <= 0) break;
                                    out.write(buf, 0, read);
                                    offset += read;
                                    remaining -= read;
                                }
                                successCount[0]++;
                            }
                        }
                    }
                } catch (Exception e) {
                    errorLogs.add("שגיאת שחזור קובץ " + node.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean hasSelectedDescendant(RecoveredEntry node) {
        if (node.isSelected()) return true;
        for (RecoveredEntry child : node.getChildren()) {
            if (hasSelectedDescendant(child)) return true;
        }
        return false;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[/\\\\?%*:|<>\"]", "_");
    }

    private class TreeEntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            if (currentDirectory == null) return 0;
            return currentDirectory.getChildren().size();
        }

        @Override
        public Object getItem(int position) {
            if (currentDirectory == null) return null;
            return currentDirectory.getChildren().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            ViewHolder holder;

            if (convertView == null) {
                itemLayout = new LinearLayout(ScanActivity.this);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                itemLayout.setPadding(pad, pad, pad, pad);
                itemLayout.setBackgroundColor(0xFFFFFFFF);

                CheckBox checkBox = new CheckBox(ScanActivity.this);
                itemLayout.addView(checkBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                ImageView ivIcon = new ImageView(ScanActivity.this);
                int iconSize = (int) (40 * getResources().getDisplayMetrics().density);
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
                textLayout.addView(tvName);

                TextView tvDetails = new TextView(ScanActivity.this);
                tvDetails.setTextSize(13);
                tvDetails.setTextColor(0xFF757575);
                textLayout.addView(tvDetails);

                Button btnDetails = new Button(ScanActivity.this);
                btnDetails.setText("פרטים");
                btnDetails.setTextSize(12);
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
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean newState = holder.checkBox.isChecked();
                    entry.setSelected(newState);
                    if (rootNode != null) rootNode.updateSelectionStateFromChildren();
                    notifyDataSetChanged();
                }
            });

            if (entry.isDirectory()) {
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
                holder.tvName.setText("📁 " + entry.getName());
                int fileCount = entry.getRecursiveFileCount();
                long totalSize = entry.getRecursiveSize();
                holder.tvDetails.setText("קבצים: " + fileCount + " • גודל: " + formatSize(totalSize));
                holder.btnDetails.setVisibility(View.GONE);
            } else {
                holder.tvName.setText(entry.getName());
                String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(entry.getModifiedTime()));
                holder.tvDetails.setText("גודל: " + formatSize(entry.getLength()) + " • " + dateStr);
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
                    holder.ivIcon.setImageResource(android.R.drawable.ic_menu_doc);
                }

                holder.btnDetails.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRawBytesHexDialog(entry);
                    }
                });
            }

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
        sb.append("אופסט: 0x").append(Long.toHexString(entry.getOffset())).append("\n");
        sb.append("גודל: ").append(entry.getLength()).append(" בייטס\n\n");
        sb.append("בתים גולמיים של השם (Hex):\n");
        
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
        tv.setTextSize(13);
        tv.setTextColor(0xFF333333);
        tv.setTextIsSelectable(true);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("פרטי קובץ גולמיים")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
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
        try {
            unregisterReceiver(scanReceiver);
        } catch (Exception ignored) {}
        if (byteSource != null) {
            byteSource.close();
        }
    }
}
