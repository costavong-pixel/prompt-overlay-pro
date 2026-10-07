package com.costavong.promptoverlay;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small, on-device reader for the document formats most useful for a
 * teleprompter script. It deliberately does not use a cloud converter or
 * retain a copy of the selected file.
 */
final class DocumentTextExtractor {
    private static final int MAX_TEXT_BYTES = 1_500_000;
    private static final int MAX_DOCX_XML_BYTES = 8_000_000;

    enum Type {
        PDF,
        DOCX,
        HTML,
        TEXT,
        UNSUPPORTED
    }

    private DocumentTextExtractor() {
    }

    static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && !cursor.isNull(column)) {
                    return cursor.getString(column);
                }
            }
        } catch (Exception ignored) {
            // A display name is only used in the user-facing confirmation.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "selected document";
    }

    static Type typeFor(Context context, Uri uri, String displayName) {
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.US);
        String mimeType = context.getContentResolver().getType(uri);
        if ("application/pdf".equals(mimeType) || lowerName.endsWith(".pdf")) {
            return Type.PDF;
        }
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType)
                || lowerName.endsWith(".docx")) {
            return Type.DOCX;
        }
        if ("text/html".equals(mimeType) || lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return Type.HTML;
        }
        if ((mimeType != null && mimeType.startsWith("text/"))
                || lowerName.endsWith(".txt")
                || lowerName.endsWith(".md")
                || lowerName.endsWith(".markdown")) {
            return Type.TEXT;
        }
        return Type.UNSUPPORTED;
    }

    static String read(Context context, Uri uri, Type type) throws IOException {
        switch (type) {
            case DOCX:
                return readDocx(context.getContentResolver(), uri);
            case HTML:
                return htmlToText(readUtf8(context.getContentResolver(), uri, MAX_TEXT_BYTES));
            case TEXT:
                return readUtf8(context.getContentResolver(), uri, MAX_TEXT_BYTES);
            default:
                throw new IOException("That document type cannot be read as text.");
        }
    }

    private static String readUtf8(ContentResolver resolver, Uri uri, int limit) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("Could not open the selected document.");
        }
        try (InputStream stream = input) {
            return new String(readLimited(stream, limit), StandardCharsets.UTF_8);
        }
    }

    private static String htmlToText(String html) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
        }
        //noinspection deprecation
        return Html.fromHtml(html).toString();
    }

    private static String readDocx(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("Could not open the selected Word document.");
        }
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return wordXmlToText(new LimitedInputStream(zip, MAX_DOCX_XML_BYTES));
                }
            }
        }
        throw new IOException("This Word document has no readable text.");
    }

    private static String wordXmlToText(InputStream input) throws IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            StringBuilder out = new StringBuilder();
            boolean addParagraphBreak = false;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                String tag = parser.getName();
                if ("p".equals(tag)) {
                    if (addParagraphBreak && out.length() > 0) {
                        out.append('\n');
                    }
                    addParagraphBreak = true;
                } else if ("t".equals(tag)) {
                    String value = parser.nextText();
                    if (value != null) {
                        out.append(value);
                    }
                } else if ("tab".equals(tag)) {
                    out.append('\t');
                } else if ("br".equals(tag) || "cr".equals(tag)) {
                    out.append('\n');
                }
                if (out.length() > MAX_TEXT_BYTES) {
                    throw new IOException("This document is too large to import as one script.");
                }
            }
            return out.toString();
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Could not read text from that Word document.", error);
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new IOException("This document is too large to import as one script.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final int limit;
        private int readTotal;

        LimitedInputStream(InputStream input, int limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (readTotal >= limit) {
                throw new IOException("This document is too large to import as one script.");
            }
            int value = super.read();
            if (value != -1) {
                readTotal++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (readTotal >= limit) {
                throw new IOException("This document is too large to import as one script.");
            }
            int allowed = Math.min(length, limit - readTotal);
            int count = super.read(buffer, offset, allowed);
            if (count != -1) {
                readTotal += count;
            }
            return count;
        }
    }
}
