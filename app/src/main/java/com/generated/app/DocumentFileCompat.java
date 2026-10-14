package com.generated.app;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.content.ContentResolver;

public class DocumentFileCompat {
    private final Context context;
    private final Uri uri;

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
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri;
        try {
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        } catch (Exception e) {
            return null;
        }
        try (Cursor c = resolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String docId = c.getString(0);
                    String name = c.getString(1);
                    if (displayName.equals(name)) {
                        Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId);
                        return new DocumentFileCompat(context, childUri);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public DocumentFileCompat createDirectory(String displayName) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri created = DocumentsContract.createDocument(resolver, uri, DocumentsContract.Document.MIME_TYPE_DIR, displayName);
            if (created != null) {
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
                return new DocumentFileCompat(context, created);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
