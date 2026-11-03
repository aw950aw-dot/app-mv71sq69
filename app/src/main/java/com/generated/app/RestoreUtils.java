package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

public class RestoreUtils {

    private static final String PREF_IMAGE_SOURCE_URI = "pref_image_source_uri";
    private static final String PREF_CONFLICT_POLICY = "pref_conflict_policy";

    public interface PolicyCallback {
        void onChosen(int conflictPolicy);
    }

    /**
     * Asks what to do with files that already exist in the destination folder. The choice is remembered
     * and applies to ALL such files in this restore (the restore runs in the background, so it cannot ask per file).
     */
    public static void askConflictPolicy(final Activity activity, final PolicyCallback callback) {
        final SharedPreferences prefs = activity.getSharedPreferences("sd_rescue_prefs", Context.MODE_PRIVATE);
        final int[] selected = {prefs.getInt(PREF_CONFLICT_POLICY, RestoreService.POLICY_SKIP)};
        if (selected[0] < RestoreService.POLICY_SKIP || selected[0] > RestoreService.POLICY_KEEP_BOTH) {
            selected[0] = RestoreService.POLICY_SKIP;
        }
        final String[] items = {
                "דלג: השאר את הקובץ הקיים ואל תשחזר אותו שוב",
                "החלף: מחק את הקובץ הקיים ושחזר אותו מחדש",
                "שמור את שניהם: הקובץ החדש יקבל מספר בסוף השם"
        };
        new AlertDialog.Builder(activity)
                .setTitle("אם קובץ כבר קיים ביעד (חל על כל הקבצים הכפולים)")
                .setSingleChoiceItems(items, selected[0], (dialog, which) -> selected[0] = which)
                .setPositiveButton("התחל שחזור", (dialog, which) -> {
                    prefs.edit().putInt(PREF_CONFLICT_POLICY, selected[0]).apply();
                    callback.onChosen(selected[0]);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    public static void startRestoreService(Context context, Uri treeUri, boolean saveAll, Uri sourceUri, String usbDeviceName) {
        startRestoreService(context, treeUri, saveAll, sourceUri, usbDeviceName, RestoreService.POLICY_LEGACY);
    }

    public static void startRestoreService(Context context, Uri treeUri, boolean saveAll, Uri sourceUri, String usbDeviceName, int conflictPolicy) {
        Intent intent = new Intent(context, RestoreService.class);
        intent.putExtra(RestoreService.EXTRA_TREE_URI, treeUri);
        intent.putExtra(RestoreService.EXTRA_SAVE_ALL, saveAll);
        intent.putExtra(RestoreService.EXTRA_CONFLICT_POLICY, conflictPolicy);

        if (sourceUri != null) {
            intent.putExtra(RestoreService.EXTRA_URI, sourceUri);
        } else if (usbDeviceName != null && !usbDeviceName.isEmpty()) {
            intent.putExtra(RestoreService.EXTRA_USB_DEVICE_NAME, usbDeviceName);
        } else {
            // Fallback to check shared preferences for image source or active device name
            try {
                SharedPreferences prefs = context.getSharedPreferences("sd_rescue_prefs", Context.MODE_PRIVATE);
                String savedImageUri = prefs.getString(PREF_IMAGE_SOURCE_URI, null);
                if (savedImageUri != null) {
                    intent.putExtra(RestoreService.EXTRA_URI, Uri.parse(savedImageUri));
                }
            } catch (Exception ignored) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
