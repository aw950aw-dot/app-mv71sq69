package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_CHANGE_DEST = 3003;
    private static final int REQUEST_CODE_OPEN_IMAGE = 3004;
    private TextView tvDestInfo;
    private TextView tvImageSourceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF8F9FA);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.settings_title);
        tvTitle.setTextSize(24);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        root.addView(tvTitle, titleParams);

        // Scrollable view for settings
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        final CheckBox cbDeepScan = new CheckBox(this);
        cbDeepScan.setText(R.string.setting_deep_scan);
        cbDeepScan.setChecked(true);
        cbDeepScan.setTextSize(16);
        int cbPad = (int) (12 * getResources().getDisplayMetrics().density);
        cbDeepScan.setPadding(cbPad, cbPad, cbPad, cbPad);
        cbDeepScan.setBackgroundColor(0xFFFFFFFF);
        
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cbParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        content.addView(cbDeepScan, cbParams);

        TextView tvCpLabel = new TextView(this);
        tvCpLabel.setText("קוד פייג׳ לשמות קצרים (8.3):");
        tvCpLabel.setTextSize(15);
        tvCpLabel.setTextColor(0xFF333333);
        content.addView(tvCpLabel);

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
        spinParams.bottomMargin = (int) (20 * getResources().getDisplayMetrics().density);
        content.addView(spinnerCp, spinParams);

        // Advanced: Image-file source selection
        TextView tvAdvHeader = new TextView(this);
        tvAdvHeader.setText("מתקדם (Advanced):");
        tvAdvHeader.setTextSize(16);
        tvAdvHeader.setTextColor(0xFF1976D2);
        tvAdvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams ahParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        ahParams.bottomMargin = 8;
        content.addView(tvAdvHeader, ahParams);

        tvImageSourceInfo = new TextView(this);
        tvImageSourceInfo.setTextSize(13);
        tvImageSourceInfo.setTextColor(0xFF666666);
        updateImageSourceInfoText();
        content.addView(tvImageSourceInfo);

        Button btnSelectImage = new Button(this);
        btnSelectImage.setText(R.string.select_image_file);
        btnSelectImage.setTextColor(0xFFFFFFFF);
        btnSelectImage.setBackgroundColor(0xFF00838F);
        int btnPad = (int) (12 * getResources().getDisplayMetrics().density);
        btnSelectImage.setPadding(btnPad, btnPad, btnPad, btnPad);
        LinearLayout.LayoutParams imgBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        imgBtnParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        btnSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, REQUEST_CODE_OPEN_IMAGE);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
                }
            }
        });
        content.addView(btnSelectImage, imgBtnParams);

        // Destination folder
        tvDestInfo = new TextView(this);
        tvDestInfo.setTextSize(13);
        tvDestInfo.setTextColor(0xFF666666);
        updateDestInfoText();
        content.addView(tvDestInfo);

        Button btnChangeDest = new Button(this);
        btnChangeDest.setText("שנה תיקיית יעד לשחזור");
        btnChangeDest.setTextColor(0xFFFFFFFF);
        btnChangeDest.setBackgroundColor(0xFF00838F);
        btnChangeDest.setPadding(btnPad, btnPad, btnPad, btnPad);
        LinearLayout.LayoutParams destBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        destBtnParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        btnChangeDest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                try {
                    startActivityForResult(intent, REQUEST_CODE_CHANGE_DEST);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "שגיאה בפתיחת בורר התיקיות", Toast.LENGTH_SHORT).show();
                }
            }
        });
        content.addView(btnChangeDest, destBtnParams);

        // About Button
        Button btnAbout = new Button(this);
        btnAbout.setText(R.string.about_title);
        btnAbout.setTextColor(0xFFFFFFFF);
        btnAbout.setBackgroundColor(0xFF455A64);
        btnAbout.setPadding(btnPad, btnPad, btnPad, btnPad);
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        aboutParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buildDateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(R.string.about_title)
                        .setMessage("SD Rescue v0.6 build " + buildDateTime + "\n\nProfessional SD Card & USB Data Recovery Tool for Android.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
        content.addView(btnAbout, aboutParams);

        Button btnSave = new Button(this);
        btnSave.setText(R.string.save_settings);
        btnSave.setTextColor(0xFFFFFFFF);
        btnSave.setBackgroundColor(0xFF1976D2);
        btnSave.setPadding(btnPad, btnPad, btnPad, btnPad);
        
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedCp = (String) spinnerCp.getSelectedItem();
                CodepageDecoder.setCodepage(selectedCp);
                Toast.makeText(SettingsActivity.this, R.string.saved_success, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        root.addView(btnSave);

        setContentView(root);
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
        }
    }
}
