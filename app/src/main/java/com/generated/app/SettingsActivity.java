package com.generated.app;

import android.app.Activity;
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
    private TextView tvDestInfo;

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
        root.addView(cbDeepScan, cbParams);

        TextView tvCpLabel = new TextView(this);
        tvCpLabel.setText("קוד פייג׳ לשמות קצרים (8.3):");
        tvCpLabel.setTextSize(15);
        tvCpLabel.setTextColor(0xFF333333);
        root.addView(tvCpLabel);

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
        root.addView(spinnerCp, spinParams);

        // Rule 6: Change destination option in Settings
        tvDestInfo = new TextView(this);
        tvDestInfo.setTextSize(13);
        tvDestInfo.setTextColor(0xFF666666);
        updateDestInfoText();
        root.addView(tvDestInfo);

        Button btnChangeDest = new Button(this);
        btnChangeDest.setText("שנה תיקיית יעד לשחזור");
        btnChangeDest.setTextColor(0xFFFFFFFF);
        btnChangeDest.setBackgroundColor(0xFF00838F);
        int btnPad = (int) (12 * getResources().getDisplayMetrics().density);
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
        root.addView(btnChangeDest, destBtnParams);

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
            tvDestInfo.setText("תיקיית יעד נוכחית: טרם נבחרה (תתבקש בשימוש ראשון)");
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
        }
    }
}
