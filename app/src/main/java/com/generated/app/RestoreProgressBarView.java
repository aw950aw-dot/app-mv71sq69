package com.generated.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

public class RestoreProgressBarView extends LinearLayout {

    private TextView tvFileName;
    private ProgressBar progressBar;
    private TextView tvPercent;
    private TextView tvFilesProgress;
    private TextView tvBytesProgress;
    private TextView tvEta;
    private Button btnCancel;

    private final BroadcastReceiver restoreReceiver = new BroadcastReceiver() {
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
                long bytesDone = intent.getLongExtra("bytes_done", 0);
                long bytesTotal = intent.getLongExtra("bytes_total", 0);
                String currentFile = intent.getStringExtra("current_file");
                long remainingSec = intent.getLongExtra("remaining_sec", 0);

                updateDisplay(restored + skipped + failed, total, percent, bytesDone, bytesTotal, currentFile, remainingSec);
            } else if (RestoreService.ACTION_COMPLETED.equals(action)) {
                setVisibility(View.GONE);
            }
        }
    };

    public RestoreProgressBarView(Context context) {
        this(context, null);
    }

    public RestoreProgressBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(LinearLayout.VERTICAL);
        setBackgroundColor(0xFFFFFFFF);
        setVisibility(View.GONE);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * density);

        // Thin top divider
        View topDivider = new View(context);
        topDivider.setBackgroundColor(0xFFE0E0E0);
        addView(topDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) Math.max(1, density)
        ));

        // Inner container with 12dp padding
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);
        addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // File name (single line, ellipsis)
        tvFileName = new TextView(context);
        tvFileName.setTextSize(14);
        tvFileName.setTextColor(0xFF212121);
        tvFileName.setSingleLine(true);
        tvFileName.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameLp.bottomMargin = (int) (6 * density);
        container.addView(tvFileName, nameLp);

        // Horizontal progress bar with percent text
        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowLp.bottomMargin = (int) (6 * density);
        container.addView(progressRow, rowLp);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressRow.addView(progressBar, new LinearLayout.LayoutParams(
                0,
                (int) (8 * density),
                1.0f
        ));

        tvPercent = new TextView(context);
        tvPercent.setTextSize(13);
        tvPercent.setTextColor(0xFF1976D2);
        tvPercent.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pctLp.leftMargin = (int) (8 * density);
        progressRow.addView(tvPercent, pctLp);

        // Files progress: "קבצים: X מתוך Y · נשארו Z"
        tvFilesProgress = new TextView(context);
        tvFilesProgress.setTextSize(12);
        tvFilesProgress.setTextColor(0xFF616161);
        container.addView(tvFilesProgress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Bytes progress: "X.XX GB מתוך Y.YY GB"
        tvBytesProgress = new TextView(context);
        tvBytesProgress.setTextSize(12);
        tvBytesProgress.setTextColor(0xFF616161);
        LinearLayout.LayoutParams bytesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bytesLp.topMargin = (int) (2 * density);
        bytesLp.bottomMargin = (int) (6 * density);
        container.addView(tvBytesProgress, bytesLp);

        // Bottom row: ETA and Cancel button
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(bottomRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

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
                (int) (36 * density)
        ));
    }

    private void updateDisplay(int doneCount, int totalCount, int percent, long bytesDone, long bytesTotal, String fileName, long remainingSec) {
        tvFileName.setText(fileName != null && !fileName.trim().isEmpty() ? fileName : "מעבד קבצים...");
        progressBar.setProgress(percent);
        tvPercent.setText(percent + "%");

        int remainingFiles = Math.max(0, totalCount - doneCount);
        tvFilesProgress.setText(String.format(Locale.getDefault(), "קבצים: %d מתוך %d · נשארו %d", doneCount, totalCount, remainingFiles));

        double gbDone = bytesDone / (1024.0 * 1024.0 * 1024.0);
        double gbTotal = bytesTotal / (1024.0 * 1024.0 * 1024.0);
        tvBytesProgress.setText(String.format(Locale.getDefault(), "%.2f GB מתוך %.2f GB", gbDone, gbTotal));

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
            getContext().registerReceiver(restoreReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            getContext().registerReceiver(restoreReceiver, filter);
        }

        ServiceState state = ServiceState.getInstance();
        if (state.getPhase() == ServiceState.Phase.RESTORING || RestoreService.isRestoreRunning()) {
            setVisibility(View.VISIBLE);
            int done = state.getFilesCount();
            int total = state.getTotalFiles() > 0 ? state.getTotalFiles() : RestoreService.getTotalFilesCount();
            int percent = state.getPercent();
            long bytesDone = state.getTotalBytesScanned() > 0 ? state.getTotalBytesScanned() : RestoreService.getCurrentBytesRestored();
            long bytesTotal = state.getTotalBytes() > 0 ? state.getTotalBytes() : RestoreService.getTotalBytesCount();
            String currentFile = state.getCurrentFileName();
            if (currentFile == null || currentFile.isEmpty()) {
                currentFile = RestoreService.getCurrentFileName();
            }
            long remainingSec = state.getRemainingSec() > 0 ? state.getRemainingSec() : RestoreService.getCurrentRemainingSec();
            updateDisplay(done, total, percent, bytesDone, bytesTotal, currentFile, remainingSec);
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            getContext().unregisterReceiver(restoreReceiver);
        } catch (Exception ignored) {}
    }
}
