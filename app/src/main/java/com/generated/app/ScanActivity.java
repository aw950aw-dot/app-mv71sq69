package com.generated.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ScanActivity extends Activity {

    public static final String EXTRA_URI = "extra_uri";
    private static final int REQUEST_CODE_SAVE_DIR = 2002;

    private Uri sourceUri;
    private ByteSource byteSource;
    private Carver carver;

    private TextView tvStatus;
    private ProgressBar progressBar;
    private ListView listView;
    private Button btnCancel;
    private Button btnSaveAll;
    private Button btnSaveSelected;
    private Button btnToggleSelectAll;

    private final List<CarvedFileHolder> fileHolders = new ArrayList<>();
    private CarvedFileListAdapter adapter;

    private boolean isScanning = false;
    private boolean isCancelled = false;
    private Thread scanThread;

    private long startTimeMillis = 0;
    private long totalBytesScanned = 0;
    private int filesFoundCount = 0;

    private final Handler mainHandler = new Handler(Looper.getMainHandler());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        sourceUri = getIntent().getParcelableExtra(EXTRA_URI);
        if (sourceUri == null) {
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            byteSource = new FileImageSource(getContentResolver(), sourceUri);
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

        // Header / Progress Card
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

        // List View of found files
        listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        listParams.bottomMargin = pad;
        root.addView(listView, listParams);

        adapter = new CarvedFileListAdapter();
        listView.setAdapter(adapter);

        // Bottom Action Bar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        root.addView(bottomBar, bottomParams);

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
        LinearLayout.LayoutParams saveAllParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        bottomBar.addView(btnSaveAll, saveAllParams);

        // Listeners
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isScanning) {
                    isCancelled = true;
                    if (scanThread != null) {
                        scanThread.interrupt();
                    }
                    tvStatus.setText(R.string.scan_cancelled);
                    btnCancel.setEnabled(false);
                } else {
                    finish();
                }
            }
        });

        btnToggleSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean allSelected = true;
                for (CarvedFileHolder holder : fileHolders) {
                    if (!holder.isSelected()) {
                        allSelected = false;
                        break;
                    }
                }
                boolean newState = !allSelected;
                for (CarvedFileHolder holder : fileHolders) {
                    holder.setSelected(newState);
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
        startTimeMillis = System.currentTimeMillis();
        totalBytesScanned = 0;
        filesFoundCount = 0;
        fileHolders.clear();
        adapter.notifyDataSetChanged();

        scanThread = new Thread(new Runnable() {
            @Override
            public void Runnable() {
                try {
                    carver.carve(byteSource, new CarverCallback() {
                        @Override
                        public void onFileFound(final CarvedFile carvedFile) {
                            if (isCancelled) return;
                            filesFoundCount++;
                            final CarvedFileHolder holder = new CarvedFileHolder(carvedFile);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    fileHolders.add(holder);
                                    adapter.notifyDataSetChanged();
                                }
                            });
                        }

                        @Override
                        public void onProgress(final long bytesScanned, final long totalBytes) {
                            if (isCancelled) return;
                            totalBytesScanned = bytesScanned;
                            final long elapsedMillis = System.currentTimeMillis() - startTimeMillis;
                            final double mbPerSec = elapsedMillis > 0 ? (bytesScanned / (1024.0 * 1024.0)) / (elapsedMillis / 1000.0) : 0;
                            final int percent = totalBytes > 0 ? (int) ((bytesScanned * 100) / totalBytes) : 0;

                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setProgress((int) ((bytesScanned * 1000) / (totalBytes > 0 ? totalBytes : 1)));
                                    String text = String.format(java.util.Locale.getDefault(),
                                            getString(R.string.progress_speed),
                                            percent, mbPerSec, filesFoundCount);
                                    tvStatus.setText(text);
                                }
                            });
                        }
                    });

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isScanning = false;
                            btnCancel.setText(getString(android.R.string.ok));
                            tvStatus.setText(R.string.scan_completed);
                        }
                    });

                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isScanning = false;
                            tvStatus.setText(R.string.scan_cancelled);
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
        if (!saveAll) {
            boolean anySelected = false;
            for (CarvedFileHolder h : fileHolders) {
                if (h.isSelected()) {
                    anySelected = true;
                    break;
                }
            }
            if (!anySelected) {
                Toast.makeText(this, R.string.no_files_found, Toast.LENGTH_SHORT).show();
                return;
            }
        }

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
            if (data != null) {
                Uri treeUri = data.getData();
                if (treeUri != null) {
                    // Check if destination might be source (basic check on string/path)
                    if (sourceUri.toString().equals(treeUri.toString()) || (sourceUri.getPath() != null && treeUri.getPath() != null && sourceUri.getPath().equals(treeUri.getPath()))) {
                        Toast.makeText(this, R.string.warning_same_source, Toast.LENGTH_LONG).show();
                    }
                    saveFilesToDirectory(treeUri, saveAllModeFlag);
                }
            }
        }
    }

    private void saveFilesToDirectory(final Uri treeUri, final boolean saveAll) {
        final List<CarvedFile> filesToSave = new ArrayList<>();
        for (CarvedFileHolder holder : fileHolders) {
            if (saveAll || holder.isSelected()) {
                filesToSave.add(holder.getCarvedFile());
            }
        }

        if (filesToSave.isEmpty()) return;

        Toast.makeText(this, "מתחיל שמירת " + filesToSave.size() + " קבצים...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentResolver resolver = getContentResolver();
                int successCount = 0;

                for (int i = 0; i < filesToSave.size(); i++) {
                    CarvedFile cf = filesToSave.get(i);
                    String fileName = "recovered_" + (i + 1) + "_" + cf.getOffset() + "." + cf.getType().getName().toLowerCase();
                    try {
                        Uri docUri = DocumentsContract.createDocument(resolver, treeUri, cf.getType().getMimeType(), fileName);
                        if (docUri != null) {
                            try (OutputStream out = resolver.openOutputStream(docUri)) {
                                if (out != null) {
                                    long remaining = cf.getLength();
                                    long offset = cf.getOffset();
                                    byte[] buffer = new byte[32768];
                                    while (remaining > 0) {
                                        int toRead = (int) Math.min(buffer.length, remaining);
                                        int read = byteSource.readAt(offset, buffer, toRead);
                                        if (read <= 0) break;
                                        out.write(buffer, 0, read);
                                        offset += read;
                                        remaining -= read;
                                    }
                                    successCount++;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                final int finalSuccess = successCount;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ScanActivity.this, R.string.saved_success + " (" + finalSuccess + "/" + filesToSave.size() + ")", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isCancelled = true;
        if (scanThread != null) {
            scanThread.interrupt();
        }
        if (byteSource != null) {
            byteSource.close();
        }
    }

    private class CarvedFileListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return fileHolders.size();
        }

        @Override
        public Object getItem(int position) {
            return fileHolders.get(position);
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
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                );
                itemLayout.addView(textLayout, textParams);

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

            final CarvedFileHolder cfh = fileHolders.get(position);
            final CarvedFile cf = cfh.getCarvedFile();

            holder.checkBox.setChecked(cfh.isSelected());
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cfh.setSelected(holder.checkBox.isChecked());
                }
            });

            holder.tvName.setText(cf.getType().getName() + " • אופסט: " + cf.getOffset());
            holder.tvDetails.setText("גודל: " + formatSize(cf.getLength()));

            // Thumbnail loading for JPEG/PNG if possible
            holder.ivThumb.setImageBitmap(null);
            if (cf.getType() == FileType.JPEG || cf.getType() == FileType.PNG) {
                // Try decoding a small thumbnail sample
                try {
                    byte[] thumbBuf = new byte[(int) Math.min(cf.getLength(), 65536)];
                    int read = byteSource.readAt(cf.getOffset(), thumbBuf, thumbBuf.length);
                    if (read > 0) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 2;
                        Bitmap bmp = BitmapFactory.decodeByteArray(thumbBuf, 0, read, opts);
                        if (bmp != null) {
                            holder.ivThumb.setImageBitmap(bmp);
                        } else {
                            holder.ivThumb.setBackgroundColor(0xFFE0E0E0);
                        }
                    }
                } catch (Exception e) {
                    holder.ivThumb.setBackgroundColor(0xFFE0E0E0);
                }
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
