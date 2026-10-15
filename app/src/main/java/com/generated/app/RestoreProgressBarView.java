package com.generated.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

public class RestoreProgressBarView extends LinearLayout {

    private final TextView tvFileName;
    private final ProgressBar progressBar;
    private final TextView tvPercent;
    private final TextView tvFilesProgress;
    private final TextView tvBytesProgress;
    private final TextView tvEta;
    private final Button btnCancel;

    private final BroadcastReceiver restoreProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();

            if (RestoreService.ACTION_PROGRESS.equals(action)) {
                setVisibility(View.VISIBLE);
                int restored = intent.getIntExtra("restored", 0);
                int skipped = intent.getIntExtra("skipped", 0);
                int failed = intent.getIntExtra("failed", 0);
                int total = intent.getIntExtra("total", 0);
                int percent = intent.getIntExtra("percent", 0);
                String eta = intent.getStringExtra("eta");
                long bytesDone = intent.getLongExtra("bytes_done", 0);
                long bytesTotal = intent.getLongExtra("bytes_total", 0);
                String currentFile = intent.getStringExtra("current_file");
                long remainingSec = intent.getLongExtra("remaining_sec", 0);

                updateState(restored + skipped + failed, total, percent, bytesDone, bytesTotal, currentFile, remainingSec, eta);
            } else if (RestoreService.ACTION_COMPLETED.equals(action)) {
                setVisibility(View.GONE);
            }
        }
    };

    public RestoreProgressBarView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(0xFFFFFFFF);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        setPadding(pad, pad, pad, pad);

        // Thin top divider
        View divider = new View(context);
        divider.setBackgroundColor(0xFFE0E0E0);
        addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (1 * getResources().getDisplayMetrics().density)
        ));

        tvFileName = new TextView(context);
        tvFileName.setTextSize(13);
        tvFileName.setTextColor(0xFF333333);
        tvFileName.setSingleLine(true);
        tvFileName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameLp.topMargin = 6;
        nameLp.bottomMargin = 4;
        addView(tvFileName, nameLp);

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        prLp.bottomMargin = 4;
        addView(progressRow, prLp);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressRow.addView(progressBar, new LinearLayout.LayoutParams(
                0,
                (int) (8 * getResources().getDisplayMetrics().density),
                1.0f
        ));

        tvPercent = new TextView(context);
        tvPercent.setTextSize(12);
        tvPercent.setTextColor(0xFF1976D2);
        tvPercent.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pctLp.leftMargin = 8;
        progressRow.addView(tvPercent, pctLp);

        tvFilesProgress = new TextView(context);
        tvFilesProgress.setTextSize(12);
        tvFilesProgress.setTextColor(0xFF555555);
        addView(tvFilesProgress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        tvBytesProgress = new TextView(context);
        tvBytesProgress.setTextSize(12);
        tvBytesProgress.setTextColor(0xFF555555);
        addView(tvBytesProgress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        brLp.topMargin = 4;
        addView(bottomRow, brLp);

        tvEta = new TextView(context);
        tvEta.setTextSize(12);
        tvEta.setTextColor(0xFF757575);
        bottomRow.addView(tvEta, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        btnCancel = new Button(context);
        btnCancel.setText("ביטול");
        btnCancel.setTextSize(12);
        btnCancel.setTextColor(0xFFD32F2F);
        btnCancel.setBackgroundColor(0xFFFFEBEE);
        btnCancel.setOnClickListener(v -> {
            Intent cancelIntent = new Intent(getContext(), RestoreService.class);
            cancelIntent.setAction(RestoreService.ACTION_CANCEL);
            getContext().startService(cancelIntent);
        });
        bottomRow.addView(btnCancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) (36 * getResources().getDisplayMetrics().density)
        ));

        setVisibility(View.GONE);
    }

    private void updateState(int doneCount, int totalCount, int percent, long bytesDone, long bytesTotal, String fileName, long remainingSec, String etaTextFallback) {
        tvFileName.setText(fileName != null && !fileName.isEmpty() ? fileName : "מעבד קבצים...");
        progressBar.setProgress(percent);
        tvPercent.setText(percent + "%");

        tvFilesProgress.setText(String.format(Locale.getDefault(), "קבצים: %d מתוך %d", doneCount, totalCount));
        tvBytesProgress.setText(String.format(Locale.getDefault(), "%.2f GB מתוך %.2f GB",
                bytesDone / (1024.0 * 1024.0 * 1024.0),
                bytesTotal / (1024.0 * 1024.0 * 1024.0)));

        String etaStr;
        if (remainingSec < 60) {
            etaStr = "פחות מדקה";
        } else {
            long mins = remainingSec / 60;
            etaStr = String.format(Locale.getDefault(), "נשארו כ-%d דקות", mins);
        }
        tvEta.setText(etaStr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter filter = new IntentFilter();
        filter.addAction(RestoreService.ACTION_PROGRESS);
        filter.addAction(RestoreService.ACTION_COMPLETED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(restoreProgressReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            getContext().registerReceiver(restoreProgressReceiver, filter);
        }

        // Check ServiceState immediately on attach
        if (RestoreService.isRestoreRunning()) {
            setVisibility(View.VISIBLE);
            ServiceState state = ServiceState.getInstance();
            updateState(state.getFilesCount(), RestoreService.getTotalFilesCount(), state.getPercent(), state.getTotalBytesScanned(), RestoreService.getTotalBytesCount(), RestoreService.getCurrentFileName(), 0, state.getEtaText());
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            getContext().unregisterReceiver(restoreProgressReceiver);
        } catch (Exception ignored) {}
    }
}
