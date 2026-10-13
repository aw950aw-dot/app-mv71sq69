package com.generated.app;

import android.app.Activity;
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
        String[] cpOptions = new String[]{"CP437", "CP862", "Windows-1255"};
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
        spinParams.bottomMargin = (int) (24 * getResources().getDisplayMetrics().density);
        root.addView(spinnerCp, spinParams);

        Button btnSave = new Button(this);
        btnSave.setText(R.string.save_settings);
        btnSave.setTextColor(0xFFFFFFFF);
        btnSave.setBackgroundColor(0xFF1976D2);
        int btnPad = (int) (14 * getResources().getDisplayMetrics().density);
        btnSave.setPadding(btnPad, btnPad, btnPad, btnPad);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedCp = (String) spinnerCp.getSelectedItem();
                CodepageDecoder.setCodepage(selectedCp);
                Toast.makeText(SettingsActivity.this, R.string.saved_success, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        root.addView(btnSave, btnParams);

        setContentView(root);
    }
}
