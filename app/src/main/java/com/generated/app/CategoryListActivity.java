package com.generated.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CategoryListActivity extends Activity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_USB_DEVICE_NAME = "extra_usb_device_name";

    private DatabaseHelper dbHelper;
    private ListView listView;
    private CategoryAdapter adapter;
    private final List<CategoryItem> categoryItems = new ArrayList<>();
    private Uri sourceUri;
    private String usbDeviceName;

    public static class CategoryItem {
        public final String categoryKey;
        public final String displayName;
        public long fileCount;
        public long totalSize;

        public CategoryItem(String categoryKey, String displayName, long fileCount, long totalSize) {
            this.categoryKey = categoryKey;
            this.displayName = displayName;
            this.fileCount = fileCount;
            this.totalSize = totalSize;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        dbHelper = DatabaseHelper.getInstance(this);
        sourceUri = getIntent().getParcelableExtra(EXTRA_URI);
        usbDeviceName = getIntent().getStringExtra(EXTRA_USB_DEVICE_NAME);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFF8F9FA);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("פילוח קבצים לפי קטגוריות");
        tvTitle.setTextSize(22);
        tvTitle.setTextColor(0xFF1976D2);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(pad, pad, pad, pad);
        root.addView(tvTitle, titleParams);

        listView = new ListView(this);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        adapter = new CategoryAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            CategoryItem item = categoryItems.get(position);
            Intent intent = new Intent(CategoryListActivity.this, CategoryFilesActivity.class);
            intent.putExtra(CategoryFilesActivity.EXTRA_CATEGORY_KEY, item.categoryKey);
            intent.putExtra(CategoryFilesActivity.EXTRA_CATEGORY_NAME, item.displayName);
            if (sourceUri != null) intent.putExtra(CategoryFilesActivity.EXTRA_URI, sourceUri);
            if (usbDeviceName != null) intent.putExtra(CategoryFilesActivity.EXTRA_USB_DEVICE_NAME, usbDeviceName);
            startActivity(intent);
        });

        loadCategories();
        setContentView(root);
    }

    private void loadCategories() {
        categoryItems.clear();
        Map<String, long[]> statsMap = dbHelper.getCategoryStats();

        categoryItems.add(createItem(FileCategory.PHOTOS, "תמונות (Photos)", statsMap));
        categoryItems.add(createItem(FileCategory.VIDEOS, "סרטונים (Videos)", statsMap));
        categoryItems.add(createItem(FileCategory.MUSIC, "מוזיקה ואודיו (Music)", statsMap));
        categoryItems.add(createItem(FileCategory.DOCUMENTS, "מסמכים (Documents)", statsMap));
        categoryItems.add(createItem(FileCategory.ARCHIVES_APPS, "ארכיונים ואפליקציות (Archives & Apps)", statsMap));
        categoryItems.add(createItem(FileCategory.OTHER, "אחר (Other)", statsMap));

        adapter.notifyDataSetChanged();
    }

    private CategoryItem createItem(String key, String displayName, Map<String, long[]> statsMap) {
        long[] val = statsMap.get(key);
        long count = val != null ? val[0] : 0;
        long size = val != null ? val[1] : 0;
        return new CategoryItem(key, displayName, count, size);
    }

    private class CategoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return categoryItems.size();
        }

        @Override
        public Object getItem(int position) {
            return categoryItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            if (convertView == null) {
                itemLayout = new LinearLayout(CategoryListActivity.this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (14 * getResources().getDisplayMetrics().density);
                itemLayout.setPadding(pad, pad, pad, pad);
                itemLayout.setBackgroundColor(0xFFFFFFFF);

                TextView tvName = new TextView(CategoryListActivity.this);
                tvName.setTextSize(16);
                tvName.setTextColor(0xFF333333);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                itemLayout.addView(tvName);

                TextView tvMeta = new TextView(CategoryListActivity.this);
                tvMeta.setTextSize(13);
                tvMeta.setTextColor(0xFF757575);
                itemLayout.addView(tvMeta);

                itemLayout.setTag(new Holder(tvName, tvMeta));
            } else {
                itemLayout = (LinearLayout) convertView;
            }

            Holder holder = (Holder) itemLayout.getTag();
            CategoryItem item = categoryItems.get(position);

            holder.tvName.setText(item.displayName);
            holder.tvMeta.setText(String.format(Locale.getDefault(), "%d קבצים • %s", item.fileCount, formatSize(item.totalSize)));

            return itemLayout;
        }

        class Holder {
            final TextView tvName;
            final TextView tvMeta;
            Holder(TextView tvName, TextView tvMeta) {
                this.tvName = tvName;
                this.tvMeta = tvMeta;
            }
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 בייט";
        final String[] units = new String[]{"בייט", "קילובייט", "מגהבייט", "ג׳יגהבייט", "טרהבייט"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }
}
