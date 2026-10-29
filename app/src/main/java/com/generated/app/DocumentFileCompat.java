package com.generated.app;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.content.ContentResolver;

public class DocumentFileCompat {
    private final Context context;
    private final Uri uri;
    // display name -> document id of this folder's children, loaded with ONE query (findFile used to list the whole
    // folder for every file, which made big folders extremely slow)
    private java.util.HashMap<String, String> childCache = null;

    private DocumentFileCompat(Context context, Uri uri) {
        this.context = context;
        this.uri = uri;
    }

    public static DocumentFileCompat fromTreeUri(Context context, Uri treeUri) {
        if (treeUri == null) return null;
        Uri docUri;
        if (DocumentsContract.isDocumentUri(context, treeUri)) {
            docUri = treeUri;
        } else {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
        }
        return new DocumentFileCompat(context, docUri);
    }

    public Uri getUri() {
        return uri;
    }

    public boolean canWrite() {
        try {
            ContentResolver resolver = context.getContentResolver();
            try (Cursor c = resolver.query(uri, new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int flags = c.getInt(0);
                    if ((flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0 || (flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    public long getLength() {
        try {
            ContentResolver resolver = context.getContentResolver();
            try (Cursor c = resolver.query(uri, new String[]{DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    return c.getLong(0);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public DocumentFileCompat findFile(String displayName) {
        if (displayName == null) return null;
        if (childCache == null) {
            childCache = loadChildren();
            if (childCache == null) return null;
        }
        String docId = childCache.get(displayName);
        if (docId == null) return null;
        return new DocumentFileCompat(context, DocumentsContract.buildDocumentUriUsingTree(uri, docId));
    }

    private java.util.HashMap<String, String> loadChildren() {
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri;
        try {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        } catch (Exception e) {
            return null;
        }
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        try (Cursor c = resolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String name = c.getString(1);
                    if (name != null && !map.containsKey(name)) map.put(name, c.getString(0));
                }
            }
        } catch (Exception e) {
            return null;
        }
        return map;
    }

    private void remember(String displayName, Uri created) {
        if (childCache == null || displayName == null || created == null) return;
        try {
            childCache.put(displayName, DocumentsContract.getDocumentId(created));
        } catch (Exception ignored) {}
    }

    public DocumentFileCompat createDirectory(String displayName) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri created = DocumentsContract.createDocument(resolver, uri, DocumentsContract.Document.MIME_TYPE_DIR, displayName);
            if (created != null) {
                remember(displayName, created);
                return new DocumentFileCompat(context, created);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public DocumentFileCompat createFile(String mimeType, String displayName) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri created = DocumentsContract.createDocument(resolver, uri, mimeType, displayName);
            if (created != null) {
                remember(displayName, created);
                return new DocumentFileCompat(context, created);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
