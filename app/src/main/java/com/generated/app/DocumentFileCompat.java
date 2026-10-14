package com.generated.app;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.content.ContentResolver;

public class DocumentFileCompat {
    private final Context context;
    private final Uri uri;
    private final boolean isRoot;

    private DocumentFileCompat(Context context, Uri uri, boolean isRoot) {
        this.context = context;
        this.uri = uri;
        this.isRoot = isRoot;
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
        return new DocumentFileCompat(context, docUri, false);
    }

    public Uri getUri() {
        return uri;
    }

    public boolean canWrite() {
        try {
            String mode = null;
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

    public boolean isDirectory() {
        try {
            ContentResolver resolver = context.getContentResolver();
            try (Cursor c = resolver.query(uri, new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String mime = c.getString(0);
                    return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public DocumentFileCompat findFile(String displayName) {
        if (displayName == null) return null;
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        try (Cursor c = resolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String docId = c.getString(0);
                    String name = c.getString(1);
                    if (displayName.equals(name)) {
                        Uri childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId);
                        return new DocumentFileCompat(context, childUri, false);
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
                return new DocumentFileCompat(context, created, false);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public DocumentFileCompat createFile(String mimeType, String displayName) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri created = DocumentsContract.createDocument(resolver, uri, mimeType, displayName);
            if (created != null) {
                return new DocumentFileCompat(context, created, false);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
