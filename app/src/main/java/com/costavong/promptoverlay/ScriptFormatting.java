package com.costavong.promptoverlay;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Stores rich script formatting as small JSON ranges so it remains local and
 * survives activity/service hand-off. Older yellow-highlight JSON remains valid.
 */
public final class ScriptFormatting {
    public static final int HIGHLIGHT_YELLOW_COLOR = Color.rgb(255, 222, 79);
    public static final int HIGHLIGHT_BLUE_COLOR = Color.rgb(43, 137, 255);
    public static final int HIGHLIGHT_PINK_COLOR = Color.rgb(232, 78, 145);
    public static final int HIGHLIGHT_GREEN_COLOR = Color.rgb(104, 190, 91);
    public static final int HIGHLIGHT_DARK_TEXT_COLOR = Color.rgb(25, 25, 25);
    public static final int HIGHLIGHT_BACKGROUND_COLOR = HIGHLIGHT_YELLOW_COLOR;
    public static final int HIGHLIGHT_TEXT_COLOR = HIGHLIGHT_DARK_TEXT_COLOR;

    private static final int STYLE_BOLD = 1;
    private static final int STYLE_ITALIC = 2;
    private static final int STYLE_UNDERLINE = 3;

    private ScriptFormatting() {
        // Utility class.
    }

    public static void toggleBold(Editable text, int start, int end) {
        toggleStyle(text, start, end, STYLE_BOLD);
    }

    public static void toggleItalic(Editable text, int start, int end) {
        toggleStyle(text, start, end, STYLE_ITALIC);
    }

    public static void toggleUnderline(Editable text, int start, int end) {
        toggleStyle(text, start, end, STYLE_UNDERLINE);
    }

    private static void toggleStyle(Editable text, int start, int end, int styleType) {
        if (!hasValidSelection(text, start, end)) {
            return;
        }
        if (isRangeCoveredByStyle(text, start, end, styleType)) {
            clearStyleInRange(text, start, end, styleType);
        } else {
            clearStyleInRange(text, start, end, styleType);
            text.setSpan(newStyleSpan(styleType), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void toggleHighlight(Editable text, int start, int end) {
        toggleHighlight(text, start, end, HIGHLIGHT_YELLOW_COLOR, HIGHLIGHT_DARK_TEXT_COLOR);
    }

    public static void toggleHighlight(
            Editable text,
            int start,
            int end,
            int backgroundColor,
            int textColor) {
        if (!hasValidSelection(text, start, end)) {
            return;
        }
        if (isRangeCoveredByHighlight(text, start, end, backgroundColor)) {
            clearHighlightsInRange(text, start, end);
        } else {
            // Selecting a new colour replaces an older highlight in this range.
            clearHighlightsInRange(text, start, end);
            text.setSpan(
                    new BackgroundColorSpan(backgroundColor),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(
                    new ForegroundColorSpan(textColor),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void clearFormatting(Editable text, int start, int end) {
        if (!hasValidSelection(text, start, end)) {
            return;
        }
        clearStyleInRange(text, start, end, STYLE_BOLD);
        clearStyleInRange(text, start, end, STYLE_ITALIC);
        clearStyleInRange(text, start, end, STYLE_UNDERLINE);
        clearHighlightsInRange(text, start, end);
    }

    public static String toStoredText(CharSequence source) {
        CharSequence safeSource = source == null ? "" : source;
        try {
            JSONObject root = new JSONObject();
            root.put("text", safeSource.toString());

            JSONArray styles = new JSONArray();
            if (safeSource instanceof Spanned) {
                Spanned spanned = (Spanned) safeSource;
                addStyleSpans(spanned, styles, Typeface.BOLD, "bold");
                addStyleSpans(spanned, styles, Typeface.ITALIC, "italic");
                addUnderlineStyles(spanned, styles);
                addHighlightStyles(spanned, styles);
            }
            root.put("styles", styles);
            return root.toString();
        } catch (JSONException exception) {
            return safeSource.toString();
        }
    }

    public static CharSequence fromStoredText(String storedText) {
        if (storedText == null || storedText.isEmpty()) {
            return new SpannableStringBuilder("");
        }

        try {
            JSONObject root = new JSONObject(storedText);
            String plainText = root.optString("text", "");
            SpannableStringBuilder result = new SpannableStringBuilder(plainText);
            JSONArray styles = root.optJSONArray("styles");
            if (styles == null) {
                return result;
            }

            for (int index = 0; index < styles.length(); index++) {
                JSONObject style = styles.optJSONObject(index);
                if (style == null) {
                    continue;
                }

                int start = clamp(style.optInt("start", -1), 0, result.length());
                int end = clamp(style.optInt("end", -1), 0, result.length());
                if (end <= start) {
                    continue;
                }

                String kind = style.optString("kind", "");
                if ("bold".equals(kind)) {
                    result.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if ("italic".equals(kind)) {
                    result.setSpan(new StyleSpan(Typeface.ITALIC), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if ("underline".equals(kind)) {
                    result.setSpan(new UnderlineSpan(), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (isHighlightKind(kind)) {
                    int backgroundColor = highlightColorFor(kind);
                    int textColor = textColorForHighlight(backgroundColor);
                    result.setSpan(new BackgroundColorSpan(backgroundColor), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    result.setSpan(new ForegroundColorSpan(textColor), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return result;
        } catch (JSONException exception) {
            // Version 1.0 stored plain text. Keep that draft usable.
            return new SpannableStringBuilder(storedText);
        }
    }

    public static String plainText(String storedText) {
        return fromStoredText(storedText).toString();
    }

    private static void addStyleSpans(Spanned text, JSONArray styles, int style, String kind)
            throws JSONException {
        StyleSpan[] spans = text.getSpans(0, text.length(), StyleSpan.class);
        for (StyleSpan span : spans) {
            if (span.getStyle() == style) {
                addStyle(styles, kind, text.getSpanStart(span), text.getSpanEnd(span));
            }
        }
    }

    private static void addUnderlineStyles(Spanned text, JSONArray styles) throws JSONException {
        UnderlineSpan[] spans = text.getSpans(0, text.length(), UnderlineSpan.class);
        for (UnderlineSpan span : spans) {
            addStyle(styles, "underline", text.getSpanStart(span), text.getSpanEnd(span));
        }
    }

    private static void addHighlightStyles(Spanned text, JSONArray styles) throws JSONException {
        BackgroundColorSpan[] spans = text.getSpans(0, text.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            String kind = highlightKindFor(span.getBackgroundColor());
            if (kind != null) {
                addStyle(styles, kind, text.getSpanStart(span), text.getSpanEnd(span));
            }
        }
    }

    private static void addStyle(JSONArray styles, String kind, int start, int end) throws JSONException {
        if (start < 0 || end <= start) {
            return;
        }
        JSONObject style = new JSONObject();
        style.put("kind", kind);
        style.put("start", start);
        style.put("end", end);
        styles.put(style);
    }

    private static boolean hasValidSelection(CharSequence text, int start, int end) {
        return text != null && start >= 0 && end > start && end <= text.length();
    }

    private static boolean isRangeCoveredByStyle(Spanned text, int start, int end, int styleType) {
        Object[] spans = text.getSpans(start, end, Object.class);
        int coveredUntil = start;
        boolean extended;
        do {
            extended = false;
            for (Object span : spans) {
                if (!isMatchingStyle(span, styleType)) {
                    continue;
                }
                int spanStart = text.getSpanStart(span);
                int spanEnd = text.getSpanEnd(span);
                if (spanStart <= coveredUntil && spanEnd > coveredUntil) {
                    coveredUntil = Math.max(coveredUntil, spanEnd);
                    extended = true;
                }
            }
        } while (extended && coveredUntil < end);
        return coveredUntil >= end;
    }

    private static boolean isRangeCoveredByHighlight(
            Spanned text, int start, int end, int backgroundColor) {
        BackgroundColorSpan[] spans = text.getSpans(start, end, BackgroundColorSpan.class);
        int coveredUntil = start;
        boolean extended;
        do {
            extended = false;
            for (BackgroundColorSpan span : spans) {
                if (span.getBackgroundColor() != backgroundColor) {
                    continue;
                }
                int spanStart = text.getSpanStart(span);
                int spanEnd = text.getSpanEnd(span);
                if (spanStart <= coveredUntil && spanEnd > coveredUntil) {
                    coveredUntil = Math.max(coveredUntil, spanEnd);
                    extended = true;
                }
            }
        } while (extended && coveredUntil < end);
        return coveredUntil >= end;
    }

    private static void clearStyleInRange(Spannable text, int start, int end, int styleType) {
        Object[] spans = text.getSpans(start, end, Object.class);
        for (Object span : spans) {
            if (!isMatchingStyle(span, styleType)) {
                continue;
            }

            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            if (spanStart >= end || spanEnd <= start) {
                continue;
            }

            int flags = text.getSpanFlags(span);
            text.removeSpan(span);
            if (spanStart < start) {
                text.setSpan(newStyleSpan(styleType), spanStart, start, flags);
            }
            if (spanEnd > end) {
                text.setSpan(newStyleSpan(styleType), end, spanEnd, flags);
            }
        }
    }

    private static void clearHighlightsInRange(Spannable text, int start, int end) {
        Object[] spans = text.getSpans(start, end, Object.class);
        for (Object span : spans) {
            if (!(span instanceof BackgroundColorSpan) && !(span instanceof ForegroundColorSpan)) {
                continue;
            }

            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            if (spanStart >= end || spanEnd <= start) {
                continue;
            }
            int flags = text.getSpanFlags(span);
            text.removeSpan(span);
            if (spanStart < start) {
                text.setSpan(copyHighlightSpan(span), spanStart, start, flags);
            }
            if (spanEnd > end) {
                text.setSpan(copyHighlightSpan(span), end, spanEnd, flags);
            }
        }
    }

    private static Object copyHighlightSpan(Object span) {
        if (span instanceof BackgroundColorSpan) {
            return new BackgroundColorSpan(((BackgroundColorSpan) span).getBackgroundColor());
        }
        return new ForegroundColorSpan(((ForegroundColorSpan) span).getForegroundColor());
    }

    private static boolean isMatchingStyle(Object span, int styleType) {
        if (styleType == STYLE_BOLD) {
            return span instanceof StyleSpan && ((StyleSpan) span).getStyle() == Typeface.BOLD;
        }
        if (styleType == STYLE_ITALIC) {
            return span instanceof StyleSpan && ((StyleSpan) span).getStyle() == Typeface.ITALIC;
        }
        return span instanceof UnderlineSpan;
    }

    private static Object newStyleSpan(int styleType) {
        if (styleType == STYLE_BOLD) {
            return new StyleSpan(Typeface.BOLD);
        }
        if (styleType == STYLE_ITALIC) {
            return new StyleSpan(Typeface.ITALIC);
        }
        return new UnderlineSpan();
    }

    private static boolean isHighlightKind(String kind) {
        return "highlight".equals(kind)
                || "highlight_yellow".equals(kind)
                || "highlight_blue".equals(kind)
                || "highlight_pink".equals(kind)
                || "highlight_green".equals(kind);
    }

    private static int highlightColorFor(String kind) {
        if ("highlight_blue".equals(kind)) {
            return HIGHLIGHT_BLUE_COLOR;
        }
        if ("highlight_pink".equals(kind)) {
            return HIGHLIGHT_PINK_COLOR;
        }
        if ("highlight_green".equals(kind)) {
            return HIGHLIGHT_GREEN_COLOR;
        }
        return HIGHLIGHT_YELLOW_COLOR;
    }

    private static int textColorForHighlight(int backgroundColor) {
        return backgroundColor == HIGHLIGHT_YELLOW_COLOR
                || backgroundColor == HIGHLIGHT_GREEN_COLOR
                ? HIGHLIGHT_DARK_TEXT_COLOR
                : Color.WHITE;
    }

    private static String highlightKindFor(int backgroundColor) {
        if (backgroundColor == HIGHLIGHT_YELLOW_COLOR) {
            return "highlight_yellow";
        }
        if (backgroundColor == HIGHLIGHT_BLUE_COLOR) {
            return "highlight_blue";
        }
        if (backgroundColor == HIGHLIGHT_PINK_COLOR) {
            return "highlight_pink";
        }
        if (backgroundColor == HIGHLIGHT_GREEN_COLOR) {
            return "highlight_green";
        }
        return null;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
