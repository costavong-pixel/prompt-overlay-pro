package com.costavong.promptoverlay;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Small, local-only script library. Scripts never leave the phone: the library is
 * serialized into the same private SharedPreferences store as the prompt settings.
 */
public final class ScriptLibrary {
    private static final String KEY_LIBRARY = "script_library_v1";
    private static final String KEY_MIGRATED = "script_library_migrated_v1";

    private ScriptLibrary() {
        // Utility class.
    }

    public static final class Entry {
        public String id;
        // Plain copies make search, filtering, and accessibility labels dependable.
        public String title;
        public String tags;
        // Rich copies preserve the text styling chosen in the title and tag fields.
        public String storedTitle;
        public String storedTags;
        public String storedText;
        public boolean archived;
        public long updatedAt;

        public Entry(String id, String title, String tags, String storedText,
                     boolean archived, long updatedAt) {
            this(
                    id,
                    title,
                    tags,
                    ScriptFormatting.toStoredText(title),
                    ScriptFormatting.toStoredText(tags),
                    storedText,
                    archived,
                    updatedAt);
        }

        public Entry(
                String id,
                String title,
                String tags,
                String storedTitle,
                String storedTags,
                String storedText,
                boolean archived,
                long updatedAt) {
            this.id = id;
            this.title = title == null || title.trim().isEmpty() ? "Untitled script" : title;
            this.tags = tags == null ? "" : tags;
            this.storedTitle = storedTitle == null || storedTitle.trim().isEmpty()
                    ? ScriptFormatting.toStoredText(this.title)
                    : storedTitle;
            this.storedTags = storedTags == null ? ScriptFormatting.toStoredText(this.tags) : storedTags;
            this.storedText = storedText;
            this.archived = archived;
            this.updatedAt = updatedAt;
        }
    }

    public static void migrateLegacyDraft(SharedPreferences preferences) {
        if (preferences.getBoolean(KEY_MIGRATED, false)) {
            return;
        }
        List<Entry> existing = load(preferences);
        if (!existing.isEmpty()) {
            preferences.edit().putBoolean(KEY_MIGRATED, true).apply();
            return;
        }

        String legacyPlainText = preferences.getString("script", "");
        String legacyStoredText = preferences.getString("script_format", "");
        String storedPlainText = legacyStoredText.trim().isEmpty()
                ? ""
                : ScriptFormatting.plainText(legacyStoredText);
        if (legacyPlainText.trim().isEmpty() && storedPlainText.trim().isEmpty()) {
            preferences.edit().putBoolean(KEY_MIGRATED, true).apply();
            return;
        }

        String storedText = legacyStoredText.trim().isEmpty()
                ? ScriptFormatting.toStoredText(legacyPlainText)
                : legacyStoredText;
        String title = firstLine(legacyPlainText);
        if (title.isEmpty()) {
            title = "My first script";
        }
        Entry migrated = new Entry(
                newId(),
                title,
                "",
                storedText,
                false,
                System.currentTimeMillis());
        saveAll(preferences, Collections.singletonList(migrated));
        preferences.edit()
                .putString("current_script_id", migrated.id)
                .putBoolean(KEY_MIGRATED, true)
                .apply();
    }

    public static List<Entry> load(SharedPreferences preferences) {
        List<Entry> result = new ArrayList<>();
        String encoded = preferences.getString(KEY_LIBRARY, "");
        if (encoded.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(encoded);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                String id = object.optString("id", "").trim();
                if (id.isEmpty()) {
                    continue;
                }
                String title = object.optString("title", "Untitled script");
                String tags = object.optString("tags", "");
                result.add(new Entry(
                        id,
                        title,
                        tags,
                        object.optString("storedTitle", ScriptFormatting.toStoredText(title)),
                        object.optString("storedTags", ScriptFormatting.toStoredText(tags)),
                        object.optString("storedText", ""),
                        object.optBoolean("archived", false),
                        object.optLong("updatedAt", 0L)));
            }
        } catch (JSONException ignored) {
            // A damaged library should not prevent the setup screen from opening.
        }
        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return Long.compare(right.updatedAt, left.updatedAt);
            }
        });
        return result;
    }

    public static Entry find(SharedPreferences preferences, String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        for (Entry entry : load(preferences)) {
            if (id.equals(entry.id)) {
                return entry;
            }
        }
        return null;
    }

    public static void upsert(SharedPreferences preferences, Entry entry) {
        List<Entry> entries = load(preferences);
        boolean replaced = false;
        for (int index = 0; index < entries.size(); index++) {
            if (entry.id.equals(entries.get(index).id)) {
                entries.set(index, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            entries.add(entry);
        }
        saveAll(preferences, entries);
    }

    public static void delete(SharedPreferences preferences, String id) {
        List<Entry> entries = load(preferences);
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (id != null && id.equals(entries.get(index).id)) {
                entries.remove(index);
            }
        }
        saveAll(preferences, entries);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        String[] lines = text.trim().split("\\R", 2);
        if (lines.length == 0) {
            return "";
        }
        String result = lines[0].trim();
        if (result.length() > 42) {
            result = result.substring(0, 42).trim() + "…";
        }
        return result;
    }

    private static void saveAll(SharedPreferences preferences, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.id);
                String storedTitle = entry.storedTitle == null
                        ? ScriptFormatting.toStoredText(entry.title)
                        : entry.storedTitle;
                String storedTags = entry.storedTags == null
                        ? ScriptFormatting.toStoredText(entry.tags)
                        : entry.storedTags;
                object.put("title", ScriptFormatting.plainText(storedTitle));
                object.put("tags", ScriptFormatting.plainText(storedTags));
                object.put("storedTitle", storedTitle);
                object.put("storedTags", storedTags);
                object.put("storedText", entry.storedText == null ? "" : entry.storedText);
                object.put("archived", entry.archived);
                object.put("updatedAt", entry.updatedAt);
                array.put(object);
            } catch (JSONException ignored) {
                // JSONObject only fails for unsupported values; all values above are safe.
            }
        }
        preferences.edit().putString(KEY_LIBRARY, array.toString()).apply();
    }
}
