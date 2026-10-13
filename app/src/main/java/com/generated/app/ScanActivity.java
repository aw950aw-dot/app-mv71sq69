package com.generated.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ScanActivity extends Activity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";
    private static final int REQUEST_CODE_SAVE_DIR = 2002;

    private Uri sourceUri;
    private String usbDeviceName;
    private ByteSource byteSource;
    private Carver carver;

    private TextView tvStatus;
    private ProgressBar progressBar;
    private ListView listView;
    private Button btnCancel;
    private Button btnSaveAll;
    private Button btnSaveSelected;
    private Button btnToggleSelectAll;

    private final List<RecoveredEntryHolder> entryHolders = new ArrayList<>();
    private RecoveredEntryAdapter adapter;

    private boolean isScanning = false;
    private boolean isCancelled = false;
    private Thread scanThread;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                UsbDevice targetDevice = null;
                for (UsbDevice d : deviceList.values()) {
                    if (d.getDeviceName().equals(usbDeviceName)) {
                        targetDevice = d;
                        break;
                    }
                }
                if (targetDevice == null) {
                    throw new Exception("USB device not found");
                }
                byteSource = new UsbMassStorageSource(this, targetDevice);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        carver = new Carver();

        setupUI();
        startScan();
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
        statusParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        topCard.addView(tvStatus, statusParams);

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
        btnParams.rightMargin = (int) (8 * getResources().getDisplayMetrics().density);
        topButtons.addView(btnCancel, btnParams);

        btnToggleSelectAll = new Button(this);
        btnToggleSelectAll.setText(R.string.deselect_all);
        btnToggleSelectAll.setTextColor(0xFF1976D2);
        btnToggleSelectAll.setBackgroundColor(0xFFE3F2FD);
        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        topButtons.addView(btnToggleSelectAll, btnParams2);

        listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        listParams.bottomMargin = pad;
        root.addView(listView, listParams);

        adapter = new RecoveredEntryAdapter();
        listView.setAdapter(adapter);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        btnSaveSelected = new Button(this);
        btnSaveSelected.setText(R.string.save_selected);
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
                if (isScanning) {
                    isCancelled = true;
                    if (scanThread != null) scanThread.interrupt();
                    tvStatus.setText(R.string.scan_cancelled);
                } else {
                    finish();
                }
            }
        });

        btnToggleSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean allSelected = true;
                for (RecoveredEntryHolder h : entryHolders) {
                    if (!h.getEntry().isSelected()) {
                        allSelected = false;
                        break;
                    }
                }
                boolean newState = !allSelected;
                for (RecoveredEntryHolder h : entryHolders) {
                    h.getEntry().setSelected(newState);
                }
                btnToggleSelectAll.setText(newState ? R.string.deselect_all : R.string.select_all);
                adapter.notifyDataSetChanged();
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

    private void startScan() {
        isScanning = true;
        isCancelled = false;
        entryHolders.clear();
        adapter.notifyDataSetChanged();

        scanThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Filesystem-aware recovery (MBR, FAT32, exFAT, deleted files, folder tree)
                    List<RecoveredEntry> fsEntries = FilesystemParser.parseFilesystem(byteSource);
                    for (RecoveredEntry entry : fsEntries) {
                        if (isCancelled) return;
                        entryHolders.add(new RecoveredEntryHolder(entry));
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                            tvStatus.setText("שחזור מערכת קבצים הושלם. נמצאו " + entryHolders.size() + " פריטים.");
                        }
                    });

                    // 2. Fallback Carver for raw unreferenced files
                    carver.carve(byteSource, new CarverCallback() {
                        @Override
                        public void onFileFound(CarvedFile carvedFile) {
                            if (isCancelled) return;
                            // Check if overlap exists
                            boolean exists = false;
                            for (RecoveredEntryHolder h : entryHolders) {
                                if (Math.abs(h.getEntry().getOffset() - carvedFile.getOffset()) < 4096) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                RecoveredEntry carvedEntry = new RecoveredEntry(
                                        "carved_" + carvedFile.getOffset() + "." + carvedFile.getType().getName().toLowerCase(),
                                        "/carved",
                                        carvedFile.getOffset(),
                                        carvedFile.getLength(),
                                        carvedFile.getType(),
                                        true,
                                        false
                                );
                                final RecoveredEntryHolder holder = new RecoveredEntryHolder(carvedEntry);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        entryHolders.add(holder);
                                        adapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onProgress(long bytesScanned, long totalBytes) {
                            if (isCancelled) return;
                            final int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setProgress(percent * 10);
                                }
                            });
                        }
                    });

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isScanning = false;
                            btnCancel.setText(android.R.string.ok);
                            tvStatus.setText(R.string.scan_completed);
                        }
                    });

                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isScanning = false;
                            tvStatus.setText(R.string.scan_completed);
                        }
                    });
                }
            }
        });
        scanThread.start();
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
                saveFilesToDirectory(data.getData(), saveAllModeFlag);
            }
        }
    }

    private void saveFilesToDirectory(final Uri treeUri, final boolean saveAll) {
        final List<RecoveredEntry> toSave = new ArrayList<>();
        for (RecoveredEntryHolder h : entryHolders) {
            if (saveAll || h.getEntry().isSelected()) {
                toSave.add(h.getEntry());
            }
        }

        if (toSave.isEmpty()) return;

        Toast.makeText(this, "שומר " + toSave.size() + " קבצים...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                int successCount = 0;
                for (int i = 0; i < toSave.size(); i++) {
                    RecoveredEntry entry = toSave.get(i);
                    try {
                        Uri docUri = DocumentsContract.createDocument(resolver, treeUri, entry.getType().getMimeType(), entry.getName());
                        if (docUri != null) {
                            try (OutputStream out = resolver.openOutputStream(docUri)) {
                                if (out != null) {
                                    long remaining = entry.getLength();
                                    long offset = entry.getOffset();
                                    byte[] buf = new byte[32768];
                                    while (remaining > 0) {
                                        int toRead = (int) Math.min(buf.length, remaining);
                                        int read = byteSource.readAt(offset, buf, toRead);
                                        if (read <= 0) break;
                                        out.write(buf, 0, read);
                                        offset += read;
                                        remaining -= read;
                                    }
                                    successCount++;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                final int finalSuccess = successCount;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ScanActivity.this, getString(R.string.saved_success) + " (" + finalSuccess + "/" + toSave.size() + ")", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private class RecoveredEntryHolder {
        private final RecoveredEntry entry;
        public RecoveredEntryHolder(RecoveredEntry entry) {
            this.entry = entry;
        }
        public RecoveredEntry getEntry() {
            return entry;
        }
    }

    private class RecoveredEntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entryHolders.size();
        }

        @Override
        public Object getItem(int position) {
            return entryHolders.get(position);
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
                int pad = (int) (12 * getResources().getDisplayMetrics().density);
                itemLayout.setPadding(pad, pad, pad, pad);
                itemLayout.setBackgroundColor(0xFFFFFFFF);

                CheckBox checkBox = new CheckBox(ScanActivity.this);
                itemLayout.addView(checkBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                ImageView ivThumb = new ImageView(ScanActivity.this);
                int thumbSize = (int) (48 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(thumbSize, thumbSize);
                imgParams.leftMargin = pad;
                imgParams.rightMargin = pad;
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemLayout.addView(ivThumb, imgParams);

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

                holder = new ViewHolder();
                holder.checkBox = checkBox;
                holder.ivThumb = ivThumb;
                holder.tvName = tvName;
                holder.tvDetails = tvDetails;
                itemLayout.setTag(holder);
            } else {
                itemLayout = (LinearLayout) convertView;
                holder = (ViewHolder) itemLayout.getTag();
            }

            final RecoveredEntryHolder eh = entryHolders.get(position);
            final RecoveredEntry entry = eh.getEntry();

            holder.checkBox.setChecked(entry.isSelected());
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    entry.setSelected(holder.checkBox.isChecked());
                }
            });

            String statusTag = entry.isDeleted() ? " [מחוק]" : "";
            holder.tvName.setText(entry.getPath() + "/" + entry.getName() + statusTag);
            holder.tvDetails.setText("גודל: " + formatSize(entry.getLength()) + " • " + entry.getType().getName());

            holder.ivThumb.setImageBitmap(null);
            if (entry.getType() == FileType.JPEG || entry.getType() == FileType.PNG) {
                try {
                    byte[] thumbBuf = new byte[(int) Math.min(entry.getLength(), 16384)];
                    int read = byteSource.readAt(entry.getOffset(), thumbBuf, thumbBuf.length);
                    if (read > 0) {
                        Bitmap bmp = BitmapFactory.decodeByteArray(thumbBuf, 0, read);
                        if (bmp != null) holder.ivThumb.setImageBitmap(bmp);
                    }
                } catch (Exception ignored) {}
            } else {
                holder.ivThumb.setBackgroundColor(0xFFE3F2FD);
            }

            return itemLayout;
        }

        class ViewHolder {
            CheckBox checkBox;
            ImageView ivThumb;
            TextView tvName;
            TextView tvDetails;
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 בייט";
        final String[] units = new String[]{"בייט", "קילובייט", "מגהבייט", "ג׳יגהבייט", "טרהבייט"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format(java.util.Locale.getDefault(), "%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
