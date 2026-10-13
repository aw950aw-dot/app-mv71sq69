package com.generated.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 1001;

    private TextView tvFileName;
    private TextView tvFileSize;
    private Button btnStartScan;
    private ByteSource currentByteSource;
    private Uri currentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        rootLayout.setPadding(padding, padding, padding, padding);
        rootLayout.setBackgroundColor(0xFFF5F5F5);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_name);
        tvTitle.setTextSize(28);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = (int) (32 * getResources().getDisplayMetrics().density);
        rootLayout.addView(tvTitle, titleParams);

        Button btnSelect = new Button(this);
        btnSelect.setText(R.string.select_image_file);
        btnSelect.setTextColor(0xFFFFFFFF);
        btnSelect.setBackgroundColor(0xFF1976D2);
        int btnPadding = (int) (16 * getResources().getDisplayMetrics().density);
        btnSelect.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        rootLayout.addView(btnSelect, btnParams);

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
        scanParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
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
        tvFileName.setTextSize(16);
        tvFileName.setTextColor(0xFF333333);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        cardLayout.addView(tvFileName, textParams);

        tvFileSize = new TextView(this);
        tvFileSize.setText(getString(R.string.file_size_label) + "-");
        tvFileSize.setTextSize(16);
        tvFileSize.setTextColor(0xFF333333);
        cardLayout.addView(tvFileSize, textParams);

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector();
            }
        });

        btnStartScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUri != null) {
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    intent.putExtra(ScanActivity.EXTRA_URI, currentUri);
                    startActivity(intent);
                }
            }
        });

        setContentView(rootLayout);
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
    protected void onDestroy() {
        super.onDestroy();
        if (currentByteSource != null) {
            currentByteSource.close();
            currentByteSource = null;
        }
    }
}
