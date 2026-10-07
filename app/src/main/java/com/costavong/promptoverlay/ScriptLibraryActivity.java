package com.costavong.promptoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** A compact, local-only script library. Editing stays in MainActivity. */
public class ScriptLibraryActivity extends Activity {
    public static final String EXTRA_CURRENT_SCRIPT_ID = "extra_current_script_id";
    public static final String EXTRA_SCRIPT_ID = "extra_script_id";
    public static final int RESULT_NEW_SCRIPT = Activity.RESULT_FIRST_USER + 12;

    private static final int SCREEN_BACKGROUND = Color.rgb(20, 24, 33);
    private static final int CARD_BACKGROUND = Color.rgb(37, 43, 58);

    private SharedPreferences preferences;
    private String currentScriptId;
    private EditText searchInput;
    private Spinner filterSpinner;
    private LinearLayout cards;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("prompt_overlay", MODE_PRIVATE);
        currentScriptId = getIntent().getStringExtra(EXTRA_CURRENT_SCRIPT_ID);
        getWindow().setStatusBarColor(SCREEN_BACKGROUND);
        getWindow().setNavigationBarColor(SCREEN_BACKGROUND);
        setContentView(createScreen());
        refresh();
    }

    private View createScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SCREEN_BACKGROUND);
        root.setLayoutDirection(AppLanguage.layoutDirection(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(SCREEN_BACKGROUND);
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(18), dp(18), dp(96));
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        Button back = compactButton("‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        back.setContentDescription(AppLanguage.t(this, "Back to scripts"));
        back.setOnClickListener(view -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(50), dp(48)));

        TextView title = label("Script library", 25, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(titleRow, matchWidth());

        TextView hint = label(
                "Tap a card to edit it. Use the menu on a card to archive or delete it.",
                14,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams hintParams = matchWidth();
        hintParams.topMargin = dp(5);
        column.addView(hint, hintParams);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.rgb(132, 145, 167));
        searchInput.setHint(AppLanguage.t(this, "Search scripts"));
        searchInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        searchInput.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(cardBackground(CARD_BACKGROUND));
        LinearLayout.LayoutParams searchParams = matchWidth();
        searchParams.topMargin = dp(18);
        column.addView(searchInput, searchParams);

        filterSpinner = styledSpinner(new String[]{"All", "Active", "Archived"});
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(148), dp(48));
        filterParams.topMargin = dp(8);
        column.addView(filterSpinner, filterParams);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardsParams = matchWidth();
        cardsParams.topMargin = dp(10);
        column.addView(cards, cardsParams);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
                // No action needed.
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                refresh();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // No action needed.
            }
        });
        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep the current filter.
            }
        });

        Button newScript = new Button(this);
        newScript.setAllCaps(false);
        newScript.setText("+ " + AppLanguage.t(this, "New script"));
        newScript.setTextColor(Color.WHITE);
        newScript.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        newScript.setTypeface(newScript.getTypeface(), Typeface.BOLD);
        newScript.setBackground(cardBackground(Color.rgb(76, 104, 238)));
        newScript.setContentDescription(AppLanguage.t(this, "New script"));
        newScript.setOnClickListener(view -> {
            setResult(RESULT_NEW_SCRIPT);
            finish();
        });
        FrameLayout.LayoutParams newScriptParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(54), Gravity.BOTTOM | Gravity.END);
        newScriptParams.setMargins(dp(18), dp(18), dp(18), dp(22));
        root.addView(newScript, newScriptParams);

        return root;
    }

    private void refresh() {
        if (cards == null) {
            return;
        }
        cards.removeAllViews();
        String query = searchInput == null ? "" : searchInput.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        int filter = filterSpinner == null ? 0 : filterSpinner.getSelectedItemPosition();
        List<ScriptLibrary.Entry> entries = ScriptLibrary.load(preferences);
        LinearLayout row = null;
        int shown = 0;
        for (ScriptLibrary.Entry entry : entries) {
            if (filter == 1 && entry.archived) {
                continue;
            }
            if (filter == 2 && !entry.archived) {
                continue;
            }
            String searchable = (entry.title + " " + entry.tags + " "
                    + ScriptFormatting.plainText(entry.storedText)).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) {
                continue;
            }
            if (shown % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = matchWidth();
                rowParams.topMargin = shown == 0 ? 0 : dp(8);
                cards.addView(row, rowParams);
            }
            if (row != null) {
                View card = createCard(entry);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(166), 1f);
                if (shown % 2 == 1) {
                    cardParams.leftMargin = dp(8);
                }
                row.addView(card, cardParams);
            }
            shown++;
        }
        if (shown == 0) {
            TextView empty = label(
                    entries.isEmpty()
                            ? "Save a script to build your local library."
                            : "No scripts match this filter.",
                    14,
                    Color.rgb(176, 190, 214));
            empty.setPadding(dp(4), dp(16), dp(4), dp(12));
            cards.addView(empty, matchWidth());
        } else if (shown % 2 == 1 && row != null) {
            View spacer = new View(this);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, dp(166), 1f);
            spacerParams.leftMargin = dp(8);
            row.addView(spacer, spacerParams);
        }
    }

    private View createCard(ScriptLibrary.Entry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(8), dp(10));
        card.setBackground(cardBackground(CARD_BACKGROUND));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(entry.title);
        card.setOnClickListener(view -> open(entry.id));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("", 15, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setText(ScriptFormatting.fromStoredText(entry.storedTitle));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button menu = compactButton("⋮");
        menu.setContentDescription(AppLanguage.t(this, "More"));
        menu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        menu.setOnClickListener(view -> showMenu(menu, entry));
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(36), dp(44)));
        card.addView(titleRow, matchWidth());

        TextView status = label(entry.archived ? "Archived" : "", 12, Color.rgb(149, 221, 194));
        status.setMaxLines(1);
        if (entry.id.equals(currentScriptId)) {
            status.setText(AppLanguage.t(this, "Current"));
        }
        card.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));

        String rawTags = entry.tags.trim();
        TextView tags = label(rawTags.isEmpty() ? "No tags" : "# " + rawTags,
                12, Color.rgb(176, 190, 214));
        tags.setMaxLines(1);
        tags.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(tags, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        TextView preview = label(ScriptFormatting.plainText(entry.storedText),
                12, Color.rgb(199, 214, 235));
        preview.setMaxLines(3);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewParams.topMargin = dp(4);
        card.addView(preview, previewParams);
        return card;
    }

    private void showMenu(View anchor, ScriptLibrary.Entry entry) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, AppLanguage.t(this, entry.archived ? "Restore" : "Archive"));
        popup.getMenu().add(0, 2, 1, AppLanguage.t(this, "Delete"));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                entry.archived = !entry.archived;
                entry.updatedAt = System.currentTimeMillis();
                ScriptLibrary.upsert(preferences, entry);
                refresh();
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDelete(entry);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(ScriptLibrary.Entry entry) {
        new AlertDialog.Builder(this)
                .setTitle(AppLanguage.t(this, "Delete script?"))
                .setMessage("“" + entry.title + "” "
                        + AppLanguage.t(this, "will be removed from this phone."))
                .setNegativeButton(AppLanguage.t(this, "Cancel"), null)
                .setPositiveButton(AppLanguage.t(this, "Delete"), (dialog, which) -> {
                    ScriptLibrary.delete(preferences, entry.id);
                    if (entry.id.equals(currentScriptId)) {
                        setResult(RESULT_NEW_SCRIPT);
                        finish();
                    } else {
                        refresh();
                    }
                })
                .show();
    }

    private void open(String scriptId) {
        Intent data = new Intent();
        data.putExtra(EXTRA_SCRIPT_ID, scriptId);
        setResult(RESULT_OK, data);
        finish();
    }

    private Spinner styledSpinner(String[] sourceItems) {
        String[] items = new String[sourceItems.length];
        for (int index = 0; index < sourceItems.length; index++) {
            items[index] = AppLanguage.t(this, sourceItems[index]);
        }
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(cardBackground(Color.rgb(45, 51, 68)));
        spinner.setPadding(dp(12), 0, dp(12), 0);
        return spinner;
    }

    private TextView label(String value, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(AppLanguage.t(this, value));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(color);
        text.setLineSpacing(dp(3), 1f);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return text;
    }

    private Button compactButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(AppLanguage.t(this, value));
        button.setTextColor(Color.rgb(218, 225, 238));
        button.setBackground(cardBackground(Color.rgb(45, 51, 68)));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable cardBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
