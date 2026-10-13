package com.generated.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class GuideActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFFF8F9FA);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.guide_title);
        tvTitle.setTextSize(24);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = (int) (20 * getResources().getDisplayMetrics().density);
        root.addView(tvTitle, titleParams);

        addGuideCard(root, "1. הפסק להשתמש בכרטיס מיד!", "ברגע ששמת לב שנמחקו קבצים או שהכרטיס נפגם, הפסק מיד כל כתיבה או צילום חדש. המשך שימוש עלול לדרוס לצמיתות את המידע המחוק.");
        addGuideCard(root, "2. חיבור בטוח למחשב או למכשיר", "השתקור בקורא כרטיסים איכותי או חבר את ההתקן ישירות למכשיר אנדרואיד התומך ב-USB OTG.");
        addGuideCard(root, "3. סריקה ושמירה למיקום נפרד", "בצע סריקה מלאה בעזרת SD Rescue, בחר את הקבצים שברצונך לשחזר, ושמור אותם תמיד בכונן או בתיקייה שונה מזו שנסרקה כדי למנוע דריסה.");
        addGuideCard(root, "4. טיפול בקבצים פגומים", "חלק מהקבצים עשויים להיות חלקיים אם הכרטיס עבר כתיבה מרובה לאחר המחיקה. בדוק תצוגה מקדימה לפני השמירה.");

        Button btnBack = new Button(this);
        btnBack.setText("חזרה");
        btnBack.setTextColor(0xFFFFFFFF);
        btnBack.setBackgroundColor(0xFF1976D2);
        int btnPad = (int) (14 * getResources().getDisplayMetrics().density);
        btnBack.setPadding(btnPad, btnPad, btnPad, btnPad);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.topMargin = (int) (20 * getResources().getDisplayMetrics().density);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(btnBack, btnParams);

        setContentView(scrollView);
    }

    private void addGuideCard(LinearLayout parent, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(0xFFFFFFFF);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        parent.addView(card, cardParams);

        TextView tvCardTitle = new TextView(this);
        tvCardTitle.setText(title);
        tvCardTitle.setTextSize(16);
        tvCardTitle.setTextColor(0xFF333333);
        tvCardTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tParams.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        card.addView(tvCardTitle, tParams);

        TextView tvCardBody = new TextView(this);
        tvCardBody.setText(body);
        tvCardBody.setTextSize(14);
        tvCardBody.setTextColor(0xFF666666);
        card.addView(tvCardBody);
    }
}
