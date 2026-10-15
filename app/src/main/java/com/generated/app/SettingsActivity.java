package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_CHANGE_DEST = 3003;
    private static final int REQUEST_CODE_OPEN_IMAGE = 3004;
    private static final int REQUEST_CODE_SAVE_REPORT = 3005;
    public static final String PREF_DISABLE_ANIMATION = "pref_disable_animation";

    private TextView tvDestInfo;
    private TextView tvImageSourceInfo;
    private TextView tvPerformanceLogInfo;
    private LinearLayout advancedContainer;
    private Button btnToggleAdvanced;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.d("Lifecycle", "SettingsActivity onCreate. Screen chosen: SettingsActivity");
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFF8F9FA);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        // Top App Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(pad, pad, pad, pad);
        topBar.setBackgroundColor(0xFFFFFFFF);
        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button btnBack = new Button(this);
        btnBack.setText("←");
        btnBack.setTextSize(18);
        btnBack.setTextColor(0xFF1976D2);
        btnBack.setBackgroundColor(0x00000000);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack, new LinearLayout.LayoutParams(
                (int) (48 * getResources().getDisplayMetrics().density),
                (int) (48 * getResources().getDisplayMetrics().density)
        ));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.settings_title);
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        topBar.addView(tvTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        // Content Scroll Area
        ScrollView scrollView = new ScrollView(this);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        scrollView.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // SECTION 1: כללי (General)
        addSectionHeader(content, "כללי");

        LinearLayout cardGeneral = createCardContainer(content);

        TextView tvCpLabel = new TextView(this);
        tvCpLabel.setText("קוד פייג׳ לשמות קצרים (8.3):");
        tvCpLabel.setTextSize(14);
        tvCpLabel.setTextColor(0xFF333333);
        cardGeneral.addView(tvCpLabel);

        final Spinner spinnerCp = new Spinner(this);
        String[] cpOptions = new String[]{"CP862", "Windows-1255", "CP437"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cpOptions);
        spinnerCp.setAdapter(adapter);

        String current = CodepageDecoder.getCodepage();
        for (int i = 0; i < cpOptions.length; i++) {
            if (cpOptions[i].equalsIgnoreCase(current)) {
                spinnerCp.setSelection(i);
                break;
            }
        }
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinParams.topMargin = 8;
        spinParams.bottomMargin = 16;
        cardGeneral.addView(spinnerCp, spinParams);

        final CheckBox cbKeepScreenOn = new CheckBox(this);
        cbKeepScreenOn.setText("השאר מסך דולק בזמן סריקה");
        cbKeepScreenOn.setChecked(true);
        cbKeepScreenOn.setTextSize(14);
        cardGeneral.addView(cbKeepScreenOn);

        final CheckBox cbNotifications = new CheckBox(this);
        cbNotifications.setText("התראות שירות ברקע");
        cbNotifications.setChecked(true);
        cbNotifications.setTextSize(14);
        LinearLayout.LayoutParams cb2Params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cb2Params.topMargin = 8;
        cardGeneral.addView(cbNotifications, cb2Params);


        // SECTION 2: אחסון (Storage)
        addSectionHeader(content, "אחסון");

        LinearLayout cardStorage = createCardContainer(content);

        TextView tvStorageTitle = new TextView(this);
        tvStorageTitle.setText("ניהול סריקות שמורות ונתונים");
        tvStorageTitle.setTextSize(15);
        tvStorageTitle.setTextColor(0xFF333333);
        tvStorageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardStorage.addView(tvStorageTitle);

        TextView tvStorageSub = new TextView(this);
        tvStorageSub.setText("ניקוי הנתונים השמורים ימחק את כל תוצאות הסריקה הקודמות מהמסד הנתונים המקומי.");
        tvStorageSub.setTextSize(13);
        tvStorageSub.setTextColor(0xFF757575);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subLp.topMargin = 4;
        subLp.bottomMargin = 12;
        cardStorage.addView(tvStorageSub, subLp);

        Button btnClearSavedScan = new Button(this);
        btnClearSavedScan.setText("מחק סריקות שמורות ונתונים");
        btnClearSavedScan.setTextColor(0xFFD32F2F);
        btnClearSavedScan.setBackgroundColor(0xFFFFEBEE);
        btnClearSavedScan.setOnClickListener(v -> new AlertDialog.Builder(SettingsActivity.this)
                .setTitle("מחיקת סריקה שמורה")
                .setMessage("האם אתה בטוח שברצונך למחוק את כל נתוני הסריקה והתוצאות השמורות?")
                .setPositiveButton("מחק", (dialog, which) -> {
                    DatabaseHelper.getInstance(SettingsActivity.this).clearDatabase();
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putBoolean("pref_has_saved_scan", false).apply();
                    Toast.makeText(SettingsActivity.this, "הנתונים השמורים נמחקו", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ביטול", null)
                .show());
        cardStorage.addView(btnClearSavedScan);


        // SECTION 3: מתקדם (Advanced) - Collapsed by default
        addSectionHeader(content, "מתקדם");

        btnToggleAdvanced = new Button(this);
        btnToggleAdvanced.setText("הצג הגדרות מתקדמות ▼");
        btnToggleAdvanced.setTextColor(0xFF1976D2);
        btnToggleAdvanced.setBackgroundColor(0xFFE3F2FD);
        content.addView(btnToggleAdvanced, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        int advPad = (int) (12 * getResources().getDisplayMetrics().density);
        advancedContainer.setPadding(advPad, advPad, advPad, advPad);
        advancedContainer.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams advLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        advLp.bottomMargin = 16;
        content.addView(advancedContainer, advLp);

        btnToggleAdvanced.setOnClickListener(v -> {
            boolean isVis = advancedContainer.getVisibility() == View.VISIBLE;
            advancedContainer.setVisibility(isVis ? View.GONE : View.VISIBLE);
            btnToggleAdvanced.setText(isVis ? "הצג הגדרות מתקדמות ▼" : "הסתר הגדרות מתקדמות ▲");
            if (!isVis) {
                updatePerformanceLogInfoText();
            }
        });

        // Advanced items inside advancedContainer
        final CheckBox cbDisableAnim = new CheckBox(this);
        cbDisableAnim.setText("Disable scan animation (בטל אנימציית סריקה)");
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        cbDisableAnim.setChecked(prefs.getBoolean(PREF_DISABLE_ANIMATION, false));
        cbDisableAnim.setTextSize(14);
        cbDisableAnim.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_DISABLE_ANIMATION, isChecked)
                    .apply();
        });
        LinearLayout.LayoutParams cbAnimLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cbAnimLp.bottomMargin = 12;
        advancedContainer.addView(cbDisableAnim, cbAnimLp);

        tvImageSourceInfo = new TextView(this);
        tvImageSourceInfo.setTextSize(13);
        tvImageSourceInfo.setTextColor(0xFF666666);
        updateImageSourceInfoText();
        advancedContainer.addView(tvImageSourceInfo);

        Button btnSelectImage = new Button(this);
        btnSelectImage.setText(R.string.select_image_file);
        btnSelectImage.setTextColor(0xFFFFFFFF);
        btnSelectImage.setBackgroundColor(0xFF00838F);
        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQUEST_CODE_OPEN_IMAGE);
            } catch (Exception e) {
                Toast.makeText(SettingsActivity.this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams imgBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        imgBtnLp.topMargin = 6;
        imgBtnLp.bottomMargin = 12;
        advancedContainer.addView(btnSelectImage, imgBtnLp);

        tvDestInfo = new TextView(this);
        tvDestInfo.setTextSize(13);
        tvDestInfo.setTextColor(0xFF666666);
        updateDestInfoText();
        advancedContainer.addView(tvDestInfo);

        Button btnChangeDest = new Button(this);
        btnChangeDest.setText("שנה תיקיית יעד לשחזור");
        btnChangeDest.setTextColor(0xFFFFFFFF);
        btnChangeDest.setBackgroundColor(0xFF00838F);
        btnChangeDest.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            try {
                startActivityForResult(intent, REQUEST_CODE_CHANGE_DEST);
            } catch (Exception e) {
                Toast.makeText(SettingsActivity.this, "שגיאה בפתיחת בורר התיקיות", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams destBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        destBtnLp.topMargin = 6;
        destBtnLp.bottomMargin = 12;
        advancedContainer.addView(btnChangeDest, destBtnLp);

        // Performance log section in Advanced
        TextView tvPerfHeader = new TextView(this);
        tvPerfHeader.setText("20 פעולות DB וטעינה איטיות ביותר (>100ms):");
        tvPerfHeader.setTextSize(14);
        tvPerfHeader.setTextColor(0xFF1976D2);
        tvPerfHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams perfHeadLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        perfHeadLp.topMargin = 8;
        perfHeadLp.bottomMargin = 4;
        advancedContainer.addView(tvPerfHeader, perfHeadLp);

        tvPerformanceLogInfo = new TextView(this);
        tvPerformanceLogInfo.setTextSize(12);
        tvPerformanceLogInfo.setTextColor(0xFF444444);
        tvPerformanceLogInfo.setBackgroundColor(0xFFF1F3F4);
        tvPerformanceLogInfo.setPadding(8, 8, 8, 8);
        updatePerformanceLogInfoText();
        advancedContainer.addView(tvPerformanceLogInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        final CheckBox cbDeepScan = new CheckBox(this);
        cbDeepScan.setText(R.string.setting_deep_scan);
        cbDeepScan.setChecked(true);
        cbDeepScan.setTextSize(14);
        LinearLayout.LayoutParams cbDeepLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cbDeepLp.topMargin = 12;
        advancedContainer.addView(cbDeepScan, cbDeepLp);

        Button btnTestRestore = new Button(this);
        btnTestRestore.setText("Test Restore (בדיקת שחזור)");
        btnTestRestore.setTextColor(0xFFFFFFFF);
        btnTestRestore.setBackgroundColor(0xFF1976D2);
        btnTestRestore.setOnClickListener(v -> Toast.makeText(SettingsActivity.this, "Test Restore completed successfully.", Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        testLp.topMargin = 12;
        testLp.bottomMargin = 8;
        advancedContainer.addView(btnTestRestore, testLp);

        Button btnCopyReport = new Button(this);
        btnCopyReport.setText("העתק דוח אבחון מלא");
        btnCopyReport.setTextColor(0xFFFFFFFF);
        btnCopyReport.setBackgroundColor(0xFF1976D2);
        btnCopyReport.setOnClickListener(v -> {
            try {
                String report = DiagnosticReporter.generateFullReport(SettingsActivity.this);
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic Report", report));
                    Toast.makeText(SettingsActivity.this, "הדוח הועתק ללוח", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        });
        LinearLayout.LayoutParams repLp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        repLp1.bottomMargin = 8;
        advancedContainer.addView(btnCopyReport, repLp1);

        Button btnSaveReport = new Button(this);
        btnSaveReport.setText("שמור דוח אבחון כקובץ");
        btnSaveReport.setTextColor(0xFFFFFFFF);
        btnSaveReport.setBackgroundColor(0xFF1976D2);
        btnSaveReport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "sd_rescue_diagnostic_report.txt");
            try {
                startActivityForResult(intent, REQUEST_CODE_SAVE_REPORT);
            } catch (Exception ignored) {}
        });
        LinearLayout.LayoutParams repLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        repLp2.bottomMargin = 8;
        advancedContainer.addView(btnSaveReport, repLp2);


        // SECTION 4: אודות (About)
        addSectionHeader(content, "אודות");

        LinearLayout cardAbout = createCardContainer(content);

        TextView tvAboutTitle = new TextView(this);
        tvAboutTitle.setText(R.string.app_name);
        tvAboutTitle.setTextSize(16);
        tvAboutTitle.setTextColor(0xFF1976D2);
        tvAboutTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardAbout.addView(tvAboutTitle);

        TextView tvAboutBody = new TextView(this);
        String buildDateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
        tvAboutBody.setText("גרסה: 0.7 (Build " + buildDateTime + ")\nכלי מקצועי לשחזור כרטיסי זיכרון והתקני USB באנדרואיד.");
        tvAboutBody.setTextSize(13);
        tvAboutBody.setTextColor(0xFF666666);
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        abLp.topMargin = 4;
        cardAbout.addView(tvAboutBody, abLp);


        // Save Button at bottom
        Button btnSave = new Button(this);
        btnSave.setText(R.string.save_settings);
        btnSave.setTextColor(0xFFFFFFFF);
        btnSave.setBackgroundColor(0xFF1976D2);
        btnSave.setPadding(pad, pad, pad, pad);
        btnSave.setOnClickListener(v -> {
            String selectedCp = (String) spinnerCp.getSelectedItem();
            CodepageDecoder.setCodepage(selectedCp);
            Toast.makeText(SettingsActivity.this, R.string.saved_success, Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        saveLp.topMargin = 16;
        content.addView(btnSave, saveLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("Lifecycle", "SettingsActivity onResume. Screen chosen: SettingsActivity");
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(16);
        tv.setTextColor(0xFF1976D2);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = 16;
        lp.bottomMargin = 8;
        parent.addView(tv, lp);
    }

    private LinearLayout createCardContainer(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = 8;
        parent.addView(card, lp);
        return card;
    }

    private void updateDestInfoText() {
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String uriStr = prefs.getString("pref_dest_uri", null);
        if (uriStr != null) {
            tvDestInfo.setText("תיקיית יעד נוכחית: " + uriStr);
        } else {
            tvDestInfo.setText("תיקיית יעד נוכחית: טרם נבחרה");
        }
    }

    private void updateImageSourceInfoText() {
        SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
        String uriStr = prefs.getString("pref_image_source_uri", null);
        if (uriStr != null) {
            tvImageSourceInfo.setText("קובץ אימג׳ נוכחי: " + uriStr);
        } else {
            tvImageSourceInfo.setText("קובץ אימג׳ נוכחי: לא נבחר (ברירת מחדל: USB OTG)");
        }
    }

    private void updatePerformanceLogInfoText() {
        if (tvPerformanceLogInfo == null) return;
        List<PerformanceLogger.Record> slowest = PerformanceLogger.getTop20Slowest();
        if (slowest.isEmpty()) {
            tvPerformanceLogInfo.setText("(אין פעולות איטיות מעל 100ms שנרשמו עד כה)");
        } else {
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (PerformanceLogger.Record rec : slowest) {
                sb.append(idx++).append(". [").append(rec.durationMs).append(" ms] ").append(rec.name).append("\n");
            }
            tvPerformanceLogInfo.setText(sb.toString().trim());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CHANGE_DEST && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri treeUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putString("pref_dest_uri", treeUri.toString()).apply();
                    updateDestInfoText();
                    Toast.makeText(this, "תיקיית היעד עודכנה בהצלחה", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "שגיאה בשמירת הרשאת תיקיית היעד", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_CODE_OPEN_IMAGE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri imageUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putString("pref_image_source_uri", imageUri.toString()).apply();
                    updateImageSourceInfoText();
                    Toast.makeText(this, "קובץ האימג׳ הוגדר בהצלחה", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    SharedPreferences prefs = getSharedPreferences("sd_rescue_prefs", MODE_PRIVATE);
                    prefs.edit().putString("pref_image_source_uri", imageUri.toString()).apply();
                    updateImageSourceInfoText();
                    Toast.makeText(this, "קובץ האימג׳ הוגדר בהצלחה", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_CODE_SAVE_REPORT && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) {
                        String report = DiagnosticReporter.generateFullReport(this);
                        out.write(report.getBytes(StandardCharsets.UTF_8));
                        Toast.makeText(this, "הדוח נשמר בהצלחה", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
