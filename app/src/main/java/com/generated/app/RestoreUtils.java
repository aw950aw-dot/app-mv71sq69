package com.generated.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

public class RestoreUtils {

    private static final String PREF_IMAGE_SOURCE_URI = "pref_image_source_uri";

    public static void startRestoreService(Context context, Uri treeUri, boolean saveAll, Uri sourceUri, String usbDeviceName) {
        Intent intent = new Intent(context, RestoreService.class);
        intent.putExtra(RestoreService.EXTRA_TREE_URI, treeUri);
        intent.putExtra(RestoreService.EXTRA_SAVE_ALL, saveAll);

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
