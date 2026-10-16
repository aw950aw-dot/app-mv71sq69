package com.generated.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;

/**
 * A small panel for the home screen: card size, used and free space, and scan time estimates.
 * It only reads from the card, in the background, and never blocks the Scan button.
 */
public class CardInfoPanel extends LinearLayout {

    private final TextView tvState;
    private final TextView tvSizes;
    private final ProgressBar barUsed;
    private final TextView tvRegular;
    private final TextView tvDeep;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private volatile int generation = 0;
    private String shownKey = null;
    private CardInfoAnalyzer.Info shownInfo = null;
    private String loadingKey = null;
    private ByteSource loadingSrc = null;

    public CardInfoPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, dp(4), 0, dp(12));
        setVisibility(View.GONE);

        tvState = makeText(14, 0xFF757575);
        tvSizes = makeText(14, 0xFF333333);
        tvRegular = makeText(14, 0xFF333333);
        tvDeep = makeText(14, 0xFF333333);

        barUsed = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        barUsed.setMax(1000);
        LinearLayout.LayoutParams barLp =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        barLp.topMargin = dp(6);
        barLp.bottomMargin = dp(8);

        addView(tvState);
        addView(tvSizes);
        addView(barUsed, barLp);
        addView(tvRegular);
        addView(tvDeep);
        showBusy();
    }

    /** Called from MainActivity every time the card state is refreshed. key = reader name + size. */
    public void load(final ByteSource src, final String key) {
        try {
            if (src == null || key == null) {
                clear();
                return;
            }
            if (ScanService.isScanRunning() || RestoreService.isRestoreRunning()) {
                generation++;
                loadingKey = null;
                loadingSrc = null;
                setVisibility(View.GONE);
                return;
            }
            if (key.equals(shownKey) && shownInfo != null) {
                render(shownInfo);
                return;
            }
            if (key.equals(loadingKey) && src == loadingSrc) {
                return;   // the same read is already running
            }

            final int gen = ++generation;
            loadingKey = key;
            loadingSrc = src;
            setVisibility(View.VISIBLE);
            showBusy();

            new Thread(() -> {
                CardInfoAnalyzer.Info result = null;
                boolean cancelledRead = false;
                try {
                    result = CardInfoAnalyzer.analyze(src, () -> gen != generation);
                } catch (IOException e) {
                    cancelledRead = CardInfoAnalyzer.isCancel(e);
                } catch (Throwable t) {
                    // fall through: shows the error text
                }
                final CardInfoAnalyzer.Info info = result;
                final boolean wasCancelled = cancelledRead;
                ui.post(() -> {
                    if (gen != generation) return;      // a newer load replaced this one
                    loadingKey = null;
                    loadingSrc = null;
                    if (info != null) {
                        shownKey = key;
                        shownInfo = info;
                        render(info);
                    } else if (!wasCancelled) {
                        showError();
                    }
                });
            }, "CardInfoPanel").start();
        } catch (Throwable t) {
            showError();
        }
    }

    /** Stops a running read (call from onPause, so it never competes with a scan). */
    public void cancel() {
        generation++;
        loadingKey = null;
        loadingSrc = null;
    }

    /** Card removed. */
    public void clear() {
        generation++;
        shownKey = null;
        shownInfo = null;
        loadingKey = null;
        loadingSrc = null;
        setVisibility(View.GONE);
    }

    private void showBusy() {
        tvState.setText("בודק את הכרטיס...");
        tvState.setVisibility(View.VISIBLE);
        tvSizes.setVisibility(View.GONE);
        barUsed.setVisibility(View.GONE);
        tvRegular.setVisibility(View.GONE);
        tvDeep.setVisibility(View.GONE);
    }

    private void showError() {
        setVisibility(View.VISIBLE);
        tvState.setText("לא ניתן לקרוא את פרטי הכרטיס");
        tvState.setVisibility(View.VISIBLE);
        tvSizes.setVisibility(View.GONE);
        barUsed.setVisibility(View.GONE);
        tvRegular.setVisibility(View.GONE);
        tvDeep.setVisibility(View.GONE);
    }

    private void render(CardInfoAnalyzer.Info i) {
        setVisibility(View.VISIBLE);
        tvState.setVisibility(View.GONE);

        StringBuilder sb = new StringBuilder("גודל: ").append(fmtSize(i.sizeBytes));
        if (i.usedBytes >= 0) {
            sb.append(" · בשימוש: ").append(fmtSize(i.usedBytes));
            sb.append(" · פנוי: ").append(fmtSize(Math.max(0, i.sizeBytes - i.usedBytes)));
        }
        tvSizes.setText(sb.toString());
        tvSizes.setVisibility(View.VISIBLE);

        if (i.usedBytes >= 0 && i.sizeBytes > 0) {
            barUsed.setProgress((int) Math.min(1000, i.usedBytes * 1000L / i.sizeBytes));
            barUsed.setVisibility(View.VISIBLE);
        } else {
            barUsed.setVisibility(View.GONE);
        }

        long[] reg = regularRange(i);
        if (reg != null) {
            tvRegular.setText("סריקה רגילה: " + formatRange(reg[0], reg[1]) + " (הערכה)");
            tvRegular.setVisibility(View.VISIBLE);
        } else {
            tvRegular.setVisibility(View.GONE);
        }

        long[] deep = deepRange(i);
        if (deep != null) {
            tvDeep.setText("סריקה עמוקה: " + formatRange(deep[0], deep[1]) + " (הערכה)");
            tvDeep.setVisibility(View.VISIBLE);
        } else {
            tvDeep.setVisibility(View.GONE);
        }
    }

    // About 3 seconds per used GB: calibrated on scans of a card with ~98 GB used (about 5 minutes).
    static long[] regularRange(CardInfoAnalyzer.Info i) {
        if (i.usedBytes < 0) return null;
        double usedGb = i.usedBytes / 1073741824.0;
        double base = Math.max(30, usedGb * 3);
        return new long[]{(long) (base * 0.75), (long) Math.ceil(base * 1.25)};
    }

    // Card size divided by the measured read speed, plus 10%.
    static long[] deepRange(CardInfoAnalyzer.Info i) {
        if (i.readSpeedBps <= 0 || i.sizeBytes <= 0) return null;
        double base = i.sizeBytes / i.readSpeedBps * 1.1;
        return new long[]{(long) (base * 0.75), (long) Math.ceil(base * 1.25)};
    }

    static String formatRange(long loSec, long hiSec) {
        if (hiSec < 60) return "פחות מדקה";
        if (hiSec < 90 * 60) {
            long lo = Math.max(1, (loSec + 59) / 60);
            long hi = Math.max(lo, (hiSec + 59) / 60);
            if (lo == hi) {
                return lo == 1 ? "כדקה אחת" : String.format(Locale.getDefault(), "כ-%d דקות", lo);
            }
            return String.format(Locale.getDefault(), "כ-%d עד %d דקות", lo, hi);
        }
        return String.format(Locale.getDefault(), "כ-%.1f עד %.1f שעות", loSec / 3600.0, hiSec / 3600.0);
    }

    static String fmtSize(long bytes) {
        double gb = bytes / 1073741824.0;
        if (gb >= 1) return String.format(Locale.getDefault(), "%.1f GB", gb);
        return String.format(Locale.getDefault(), "%.0f MB", bytes / 1048576.0);
    }

    private TextView makeText(int sp, int color) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setPadding(0, dp(2), 0, dp(2));
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
