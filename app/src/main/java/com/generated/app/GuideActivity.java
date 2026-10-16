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
        tvTitle.setTextSize(22);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = (int) (20 * getResources().getDisplayMetrics().density);
        root.addView(tvTitle, titleParams);

        addGuideCard(root, "1. אל תסכים לפרמט!", "כשהטלפון מציע לפרמט את הכרטיס, אל תאשר. פירמוט מוחק את מבנה התיקיות ומקשה על ההצלה. אל תכתוב לכרטיס עד שהקבצים נשמרו במקום אחר.");
        addGuideCard(root, "2. חבר דרך קורא כרטיסים (OTG)", "חבר קורא כרטיסים לטלפון בכבל OTG. כשאנדרואיד שואל איזו אפליקציה לפתוח, בחר SD Rescue וסמן 'תמיד'. אם החיבור מתנתק, נסה כבל או קורא אחר.");
        addGuideCard(root, "3. סרוק את הכרטיס", "לחץ 'סרוק את הכרטיס'. האפליקציה קוראת את מבנה התיקיות כפי שהוא, ובכרטיס גדול זה לוקח כמה דקות. ההתקדמות מופיעה בהתראה, ואפשר לסגור את האפליקציה בזמן הסריקה.");
        addGuideCard(root, "4. בחר מה להוציא", "בחר קטגוריה (תמונות, סרטונים, מוזיקה...) כדי להוציא רק סוג אחד של קבצים, או עיין בתיקיות כפי שהן בכרטיס וסמן תיקיות וקבצים.");
        addGuideCard(root, "5. שמור למקום אחר", "בחר תיקיית יעד בזיכרון הטלפון או בכרטיס אחר, לא בכרטיס עצמו. האפליקציה רק קוראת מהכרטיס ואינה כותבת אליו.");
        addGuideCard(root, "6. אם חסרים קבצים", "אם תיקייה או קבצים לא מופיעים, סמן בהגדרות הסריקה 'סריקה עמוקה': היא מחפשת קבצים לפי סוג גם בלי מבנה תיקיות. קבצים ששחזורם נכשל מופיעים בדוח הבעיות.");
        addGuideCard(root, "7. אחרי שהכול נשמר", "בדוק שהקבצים נפתחים כמו שצריך ביעד. רק אז אפשר לפרמט את הכרטיס דרך הגדרות האחסון של אנדרואיד ולהשתמש בו שוב.");

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
