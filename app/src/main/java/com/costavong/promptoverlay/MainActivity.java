package com.costavong.promptoverlay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * Private setup screen for the floating teleprompter.
 * Your camera records the video; this app never asks for camera or microphone access.
 */
public class MainActivity extends Activity {
    public static final String EXTRA_TRIAL_EXPIRED = "extra_trial_expired";

    private static final int REQUEST_NOTIFICATION_PERMISSION = 101;
    private static final int REQUEST_IMPORT_OCR_IMAGE = 102;
    private static final int REQUEST_IMPORT_DOCUMENT = 103;
    private static final int REQUEST_IMPORT_GOOGLE_DRIVE_DOCUMENT = 104;
    private static final int REQUEST_SCRIPT_LIBRARY = 105;
    private static final int MAX_PDF_IMPORT_PAGES = 30;
    private static final int MIN_SCROLL_SPEED = 10;
    private static final int MAX_SCROLL_SPEED = 180;
    private static final int MIN_HEIGHT_OVERLAY = 35;
    private static final int MAX_HEIGHT_OVERLAY = 75;
    private static final int SCREEN_BACKGROUND = Color.rgb(20, 24, 33);
    private static final String PLAY_PACKAGE_ID = "com.costavong.promptoverlay";

    private EditText scriptInput;
    private EditText scriptTitleInput;
    private EditText scriptTagsInput;
    private EditText formatTarget;
    private HorizontalScrollView formatToolbar;
    private LinearLayout titleFormatToolbarHost;
    private LinearLayout tagsFormatToolbarHost;
    private LinearLayout scriptFormatToolbarHost;
    private String currentScriptId;
    private boolean loadingScript;
    private SeekBar speedSlider;
    private SeekBar textSizeSlider;
    private SeekBar backgroundOpacitySlider;
    private SeekBar heightOverlaySlider;
    private SeekBar widthOverlaySlider;
    private SeekBar sideMarginSlider;
    private SeekBar lineSpacingSlider;
    private SeekBar startDelaySlider;
    private TextView speedValue;
    private TextView textSizeValue;
    private TextView backgroundOpacityValue;
    private TextView heightOverlayValue;
    private TextView widthOverlayValue;
    private TextView sideMarginValue;
    private TextView lineSpacingValue;
    private TextView startDelayValue;
    private android.widget.Switch repeatToggle;
    private android.widget.Switch browserRemoteToggle;
    private SharedPreferences preferences;
    private String selectedFont = PromptFont.CLEAN;
    private String selectedTextColor = PromptTextColor.WHITE;
    private String selectedTextAlignment = PromptTextAlignment.LEFT;
    private Spinner fontSpinner;
    private Spinner textColorSpinner;
    private Spinner textAlignmentSpinner;
    private TextRecognizer textRecognizer;
    private TextView trialStatus;
    private Button unlockButton;
    private boolean screenReady;
    private PdfImportSession activePdfImport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("prompt_overlay", MODE_PRIVATE);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        configureSystemBars();
        setContentView(createScreen());
        refreshAccessUi();
        if (!BuildConfig.PRO_TEST || getIntent().getBooleanExtra("open_basic", false)) {
            requestNotificationPermissionIfNeeded();
        }
        showTrialExpiryIfRequested(getIntent());
        if (BuildConfig.PRO_TEST && !getIntent().getBooleanExtra("open_basic", false)) {
            startActivity(new android.content.Intent(this, com.costavong.promptoverlay.pro.ProActivity.class));
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraft();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showTrialExpiryIfRequested(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_OCR_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            importOcrImage(data.getData());
        } else if ((requestCode == REQUEST_IMPORT_DOCUMENT
                || requestCode == REQUEST_IMPORT_GOOGLE_DRIVE_DOCUMENT) && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            importDocument(data.getData());
        } else if (requestCode == REQUEST_SCRIPT_LIBRARY) {
            if (resultCode == RESULT_OK && data != null) {
                String scriptId = data.getStringExtra(ScriptLibraryActivity.EXTRA_SCRIPT_ID);
                if (scriptId != null && !scriptId.trim().isEmpty()) {
                    openSavedScript(scriptId);
                }
            } else if (resultCode == ScriptLibraryActivity.RESULT_NEW_SCRIPT) {
                startNewScript();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        closePdfImport();
        super.onDestroy();
    }

    private void openScriptLibrary() {
        saveDraft();
        Intent intent = new Intent(this, ScriptLibraryActivity.class);
        intent.putExtra(ScriptLibraryActivity.EXTRA_CURRENT_SCRIPT_ID, currentScriptId);
        startActivityForResult(intent, REQUEST_SCRIPT_LIBRARY);
    }

    private View createScreen() {
        final FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(SCREEN_BACKGROUND);
        screen.setLayoutDirection(AppLanguage.layoutDirection(this));

        final ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        // Android 15+ lays target-SDK 35+ content edge-to-edge. Keep the
        // editor inside the status/navigation-bar safe areas so the last
        // action is never hidden behind the system navigation buttons.
        scrollView.setClipToPadding(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        final int horizontalPadding = dp(18);
        final int topPadding = dp(22);
        final int baseBottomPadding = dp(24);
        final LinearLayout formatRow = new LinearLayout(this);
        formatToolbar = new HorizontalScrollView(this);
        formatToolbar.setHorizontalScrollBarEnabled(false);
        formatToolbar.setFillViewport(false);
        formatToolbar.setBackgroundColor(SCREEN_BACKGROUND);
        formatToolbar.addView(formatRow, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));
        final LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(horizontalPadding, topPadding, horizontalPadding, baseBottomPadding);
        scrollView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int statusBarHeight;
                int navigationBarHeight;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets systemBars = insets.getInsets(
                            WindowInsets.Type.systemBars());
                    statusBarHeight = systemBars.top;
                    navigationBarHeight = systemBars.bottom;
                } else {
                    statusBarHeight = insets.getSystemWindowInsetTop();
                    navigationBarHeight = insets.getSystemWindowInsetBottom();
                }
                // Some Samsung edge-to-edge configurations report a zero
                // bottom inset even while the gesture/navigation area is
                // visible. The fallback keeps a comfortable touch target.
                int safeTop = Math.max(statusBarHeight, dp(18));
                int safeBottom = Math.max(navigationBarHeight, dp(72));
                scrollView.setPadding(0, safeTop, 0, safeBottom);
                column.setPadding(
                        horizontalPadding,
                        0,
                        horizontalPadding,
                        baseBottomPadding);
                return insets;
            }
        });
        scrollView.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        screen.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Keep the product name on its own full-width line. The previous
        // header shared a narrow row with four links, which forced the name
        // to wrap as "PromptOv / erlay" on a normal phone.
        TextView title = text("Prompt Overlay", 28, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setSingleLine(true);
        title.setMaxLines(1);
        title.setEllipsize(null);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        column.addView(title, matchWidth());

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        TextView buildVersion = text(
                "v" + BuildConfig.VERSION_NAME + " (b" + BuildConfig.VERSION_CODE + ")",
                11,
                Color.rgb(176, 190, 214));
        buildVersion.setGravity(Gravity.CENTER_VERTICAL);
        buildVersion.setPadding(dp(4), dp(8), dp(5), dp(8));
        buildVersion.setMinHeight(dp(40));
        buildVersion.setContentDescription("Build version " + BuildConfig.VERSION_NAME
                + ", build " + BuildConfig.VERSION_CODE);
        titleRow.addView(buildVersion, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView privacy = text("Privacy", 12, Color.rgb(149, 221, 194));
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        privacy.setPadding(dp(12), dp(8), dp(2), dp(8));
        privacy.setMinHeight(dp(40));
        privacy.setPaintFlags(privacy.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        privacy.setClickable(true);
        privacy.setFocusable(true);
        privacy.setContentDescription("Read privacy information");
        privacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPrivacy();
            }
        });
        titleRow.addView(privacy, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView roadmap = text("Roadmap", 12, Color.rgb(149, 221, 194));
        roadmap.setGravity(Gravity.CENTER_VERTICAL);
        roadmap.setPadding(dp(6), dp(8), 0, dp(8));
        roadmap.setMinHeight(dp(40));
        roadmap.setPaintFlags(roadmap.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        roadmap.setClickable(true);
        roadmap.setFocusable(true);
        roadmap.setContentDescription("See planned features");
        roadmap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showRoadmap();
            }
        });
        titleRow.addView(roadmap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView rate = text("Rate", 12, Color.rgb(149, 221, 194));
        rate.setGravity(Gravity.CENTER_VERTICAL);
        rate.setPadding(dp(6), dp(8), 0, dp(8));
        rate.setMinHeight(dp(40));
        rate.setPaintFlags(rate.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        rate.setClickable(true);
        rate.setFocusable(true);
        rate.setContentDescription("Rate Prompt Overlay on Google Play");
        rate.setOnClickListener(view -> openPlayStoreListing());
        titleRow.addView(rate, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams titleRowParams = matchWidth();
        titleRowParams.topMargin = dp(2);
        column.addView(titleRow, titleRowParams);

        // Keep the main setup screen scannable: common actions are grouped into
        // five predictable sections, while advanced sections can stay collapsed.
        SectionGroup scriptSection = addSection(column, "Script", true);
        LinearLayout scriptContent = scriptSection.content;
        SectionGroup importSection = addSection(column, "Import", true);
        LinearLayout importContent = importSection.content;
        SectionGroup appearanceSection = addSection(column, "Appearance", false);
        LinearLayout appearanceContent = appearanceSection.content;
        SectionGroup remoteSection = addSection(column, "Remote Control", false);
        LinearLayout remoteContent = remoteSection.content;
        SectionGroup promptSection = addSection(column, "Prompt Controls", true);
        LinearLayout promptContent = promptSection.content;

        trialStatus = text("", 13, Color.rgb(149, 221, 194));
        LinearLayout.LayoutParams trialStatusParams = matchWidth();
        trialStatusParams.topMargin = dp(4);
        scriptContent.addView(trialStatus, trialStatusParams);

        unlockButton = smallSecondaryButton("Test build · not for sale");
        unlockButton.setContentDescription("This test build has no purchase flow");
        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showUnlockDialog(false);
            }
        });
        LinearLayout.LayoutParams unlockButtonParams = matchWidth();
        unlockButtonParams.topMargin = dp(8);
        scriptContent.addView(unlockButton, unlockButtonParams);

        ScriptLibrary.migrateLegacyDraft(preferences);
        currentScriptId = preferences.getString("current_script_id", null);

        Button libraryButton = secondaryButton("Open script library");
        libraryButton.setContentDescription("Browse, search, archive, or delete saved scripts");
        libraryButton.setOnClickListener(view -> openScriptLibrary());
        LinearLayout.LayoutParams libraryButtonParams = matchWidth();
        libraryButtonParams.topMargin = dp(18);
        scriptContent.addView(libraryButton, libraryButtonParams);

        TextView libraryHint = text(
                "Your saved scripts stay on this phone. Open the library to search and manage them.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams libraryHintParams = matchWidth();
        libraryHintParams.topMargin = dp(5);
        scriptContent.addView(libraryHint, libraryHintParams);

        LinearLayout editorActionRow = new LinearLayout(this);
        editorActionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button newScriptButton = smallSecondaryButton("Save & new");
        newScriptButton.setContentDescription("Save this script and start another script");
        newScriptButton.setOnClickListener(view -> startNewScript());
        editorActionRow.addView(newScriptButton, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button saveScriptButton = smallSecondaryButton("Save changes");
        saveScriptButton.setContentDescription("Save changes to the current script");
        saveScriptButton.setOnClickListener(view -> saveCurrentScript(true));
        LinearLayout.LayoutParams saveScriptButtonParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        saveScriptButtonParams.leftMargin = dp(8);
        editorActionRow.addView(saveScriptButton, saveScriptButtonParams);
        LinearLayout.LayoutParams editorActionParams = matchWidth();
        editorActionParams.topMargin = dp(12);
        scriptContent.addView(editorActionRow, editorActionParams);

        TextView editorActionHint = text(
                "Save changes updates this script. Save & new keeps it and starts another one.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams editorActionHintParams = matchWidth();
        editorActionHintParams.topMargin = dp(6);
        scriptContent.addView(editorActionHint, editorActionHintParams);

        TextView scriptTitleLabel = text("Script title", 15, Color.WHITE);
        scriptTitleLabel.setTypeface(scriptTitleLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams scriptTitleLabelParams = matchWidth();
        scriptTitleLabelParams.topMargin = dp(16);
        scriptContent.addView(scriptTitleLabel, scriptTitleLabelParams);

        scriptTitleInput = new EditText(this);
        scriptTitleInput.setSingleLine(true);
        scriptTitleInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        scriptTitleInput.setTextColor(Color.WHITE);
        scriptTitleInput.setHintTextColor(Color.rgb(132, 145, 167));
        scriptTitleInput.setHint(AppLanguage.t(this, "Example: Monday product video"));
        scriptTitleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        scriptTitleInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        scriptTitleInput.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        scriptTitleInput.setPadding(dp(14), 0, dp(14), 0);
        scriptTitleInput.setBackground(cardBackground(Color.rgb(37, 43, 58)));
        LinearLayout.LayoutParams scriptTitleParams = matchWidth();
        scriptTitleParams.topMargin = dp(6);
        scriptContent.addView(scriptTitleInput, scriptTitleParams);

        titleFormatToolbarHost = createFormatToolbarHost();
        scriptContent.addView(titleFormatToolbarHost, matchWidth());

        TextView scriptTagsLabel = text("Tags (optional)", 15, Color.WHITE);
        scriptTagsLabel.setTypeface(scriptTagsLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams scriptTagsLabelParams = matchWidth();
        scriptTagsLabelParams.topMargin = dp(12);
        scriptContent.addView(scriptTagsLabel, scriptTagsLabelParams);

        scriptTagsInput = new EditText(this);
        scriptTagsInput.setSingleLine(true);
        scriptTagsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        scriptTagsInput.setTextColor(Color.WHITE);
        scriptTagsInput.setHintTextColor(Color.rgb(132, 145, 167));
        scriptTagsInput.setHint(AppLanguage.t(this, "e.g. UGC, launch, draft"));
        scriptTagsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        scriptTagsInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        scriptTagsInput.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        scriptTagsInput.setPadding(dp(14), 0, dp(14), 0);
        scriptTagsInput.setBackground(cardBackground(Color.rgb(37, 43, 58)));
        LinearLayout.LayoutParams scriptTagsParams = matchWidth();
        scriptTagsParams.topMargin = dp(6);
        scriptContent.addView(scriptTagsInput, scriptTagsParams);

        tagsFormatToolbarHost = createFormatToolbarHost();
        scriptContent.addView(tagsFormatToolbarHost, matchWidth());

        TextView scriptLabel = text("Your script", 17, Color.WHITE);
        scriptLabel.setTypeface(scriptLabel.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = matchWidth();
        labelParams.topMargin = dp(18);
        scriptContent.addView(scriptLabel, labelParams);

        scriptInput = new EditText(this);
        scriptInput.setHintTextColor(Color.rgb(132, 145, 167));
        scriptInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        scriptInput.setGravity(Gravity.TOP | Gravity.START);
        scriptInput.setHint(AppLanguage.t(this,
                "Paste your script here. Short lines are easier to read while recording."));
        scriptInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        scriptInput.setSingleLine(false);
        scriptInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        scriptInput.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        scriptInput.setMinLines(10);
        scriptInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        scriptInput.setBackground(cardBackground(Color.rgb(37, 43, 58)));
        selectedFont = PromptFont.normalize(
                preferences.getString("font", PromptFont.CLEAN));
        selectedTextColor = PromptTextColor.normalize(
                preferences.getString("text_color", PromptTextColor.WHITE));
        selectedTextAlignment = PromptTextAlignment.normalize(
                preferences.getString("text_alignment", PromptTextAlignment.LEFT));
        scriptInput.setTextColor(PromptTextColor.colorFor(selectedTextColor));
        scriptInput.setTypeface(PromptFont.typefaceFor(selectedFont));
        ScriptLibrary.Entry currentEntry = ScriptLibrary.find(preferences, currentScriptId);
        if (currentEntry != null) {
            scriptTitleInput.setText(ScriptFormatting.fromStoredText(currentEntry.storedTitle));
            scriptTagsInput.setText(ScriptFormatting.fromStoredText(currentEntry.storedTags));
            scriptInput.setText(ScriptFormatting.fromStoredText(currentEntry.storedText));
        } else {
            String savedFormatting = preferences.getString("script_format", null);
            if (savedFormatting != null) {
                scriptInput.setText(ScriptFormatting.fromStoredText(savedFormatting));
            } else {
                String savedScript = preferences.getString("script", "");
                if (!savedScript.isEmpty()) {
                    scriptInput.setText(savedScript);
                }
            }
            scriptTitleInput.setText(ScriptLibrary.firstLine(scriptInput.getText().toString()));
        }
        LinearLayout.LayoutParams scriptParams = matchWidth();
        scriptParams.topMargin = dp(8);
        scriptContent.addView(scriptInput, scriptParams);

        TextView formatTip = text(
                "Select words, then use the text-style bar below. It works on the title, tags, or script you are editing.",
                13, Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams formatTipParams = matchWidth();
        formatTipParams.topMargin = dp(8);
        scriptContent.addView(formatTip, formatTipParams);

        scriptFormatToolbarHost = createFormatToolbarHost();
        scriptContent.addView(scriptFormatToolbarHost, matchWidth());

        TextView fontLabel = text("Reading font", 17, Color.WHITE);
        fontLabel.setTypeface(fontLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams fontLabelParams = matchWidth();
        fontLabelParams.topMargin = dp(18);
        appearanceContent.addView(fontLabel, fontLabelParams);

        fontSpinner = styledSpinner(new String[]{"Clean", "Classic", "Mono", "Compact"});
        fontSpinner.setSelection(fontIndex(selectedFont));
        fontSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectFont(fontAt(position));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep the last selection.
            }
        });
        LinearLayout.LayoutParams fontSpinnerParams = matchWidth();
        fontSpinnerParams.topMargin = dp(7);
        appearanceContent.addView(fontSpinner, fontSpinnerParams);

        TextView colorLabel = text("Prompt text colour", 17, Color.WHITE);
        colorLabel.setTypeface(colorLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams colorLabelParams = matchWidth();
        colorLabelParams.topMargin = dp(14);
        appearanceContent.addView(colorLabel, colorLabelParams);

        textColorSpinner = styledSpinner(PromptTextColor.labels());
        textColorSpinner.setSelection(PromptTextColor.indexOf(selectedTextColor));
        textColorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectTextColor(PromptTextColor.valueAt(position));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep the last selection.
            }
        });
        LinearLayout.LayoutParams colorSpinnerParams = matchWidth();
        colorSpinnerParams.topMargin = dp(7);
        appearanceContent.addView(textColorSpinner, colorSpinnerParams);

        TextView alignmentLabel = text("Reading alignment", 17, Color.WHITE);
        alignmentLabel.setTypeface(alignmentLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams alignmentLabelParams = matchWidth();
        alignmentLabelParams.topMargin = dp(14);
        appearanceContent.addView(alignmentLabel, alignmentLabelParams);

        textAlignmentSpinner = styledSpinner(PromptTextAlignment.labels());
        textAlignmentSpinner.setSelection(PromptTextAlignment.indexOf(selectedTextAlignment));
        textAlignmentSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectTextAlignment(PromptTextAlignment.valueAt(position));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep the last selection.
            }
        });
        LinearLayout.LayoutParams alignmentSpinnerParams = matchWidth();
        alignmentSpinnerParams.topMargin = dp(7);
        appearanceContent.addView(textAlignmentSpinner, alignmentSpinnerParams);

        TextView languageLabel = text("App language", 17, Color.WHITE);
        languageLabel.setTypeface(languageLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams languageLabelParams = matchWidth();
        languageLabelParams.topMargin = dp(14);
        appearanceContent.addView(languageLabel, languageLabelParams);

        Spinner languageSpinner = styledSpinner(AppLanguage.languageLabels());
        languageSpinner.setSelection(AppLanguage.indexOf(this));
        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedLanguage = AppLanguage.codeAt(position);
                if (!selectedLanguage.equals(AppLanguage.current(MainActivity.this))) {
                    saveDraft();
                    AppLanguage.set(MainActivity.this, selectedLanguage);
                    recreate();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep the selected language.
            }
        });
        LinearLayout.LayoutParams languageSpinnerParams = matchWidth();
        languageSpinnerParams.topMargin = dp(7);
        appearanceContent.addView(languageSpinner, languageSpinnerParams);

        Button googleDriveButton = secondaryButton("Import from Google Drive");
        googleDriveButton.setContentDescription(
                "Open Google Drive and import a Word, PDF, text, Markdown, or HTML document into the script editor");
        googleDriveButton.setOnClickListener(view -> chooseGoogleDriveDocument());
        LinearLayout.LayoutParams googleDriveButtonParams = matchWidth();
        googleDriveButtonParams.topMargin = dp(14);
        importContent.addView(googleDriveButton, googleDriveButtonParams);

        TextView googleDriveHint = text(
                "Opens the Google Drive app. Choose a document from your signed-in Drive; Prompt Overlay only reads the file you choose.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams googleDriveHintParams = matchWidth();
        googleDriveHintParams.topMargin = dp(5);
        importContent.addView(googleDriveHint, googleDriveHintParams);

        Button phoneImportButton = secondaryButton("Import from Files or Photo");
        phoneImportButton.setContentDescription(
                "Choose a Word, PDF, text, Markdown, HTML document, or photo and import its text into the script editor");
        phoneImportButton.setOnClickListener(view -> showPhoneImportChooser());
        LinearLayout.LayoutParams phoneImportButtonParams = matchWidth();
        phoneImportButtonParams.topMargin = dp(12);
        importContent.addView(phoneImportButton, phoneImportButtonParams);

        TextView phoneImportHint = text(
                "Choose a document or photo/screenshot. Documents and OCR are processed on this phone.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams phoneImportHintParams = matchWidth();
        phoneImportHintParams.topMargin = dp(5);
        importContent.addView(phoneImportHint, phoneImportHintParams);

        Button remoteControlButton = secondaryButton("Bluetooth connect (remote control)");
        remoteControlButton.setContentDescription(
                "Set up a Bluetooth keyboard, presentation clicker, foot pedal, or gamepad remote");
        remoteControlButton.setOnClickListener(view -> startActivity(
                new Intent(MainActivity.this, RemoteControlSetupActivity.class)));
        LinearLayout.LayoutParams remoteControlButtonParams = matchWidth();
        remoteControlButtonParams.topMargin = dp(14);
        remoteContent.addView(remoteControlButton, remoteControlButtonParams);

        TextView remoteControlHint = text(
                "Works with standard Bluetooth or USB keyboard, clicker, foot pedal, media, and gamepad controls. "
                        + "You can set your own buttons.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams remoteControlHintParams = matchWidth();
        remoteControlHintParams.topMargin = dp(5);
        remoteContent.addView(remoteControlHint, remoteControlHintParams);

        TextView browserRemoteStepsTitle = text("Wi-Fi connect (remote control) — 3 steps", 17, Color.WHITE);
        browserRemoteStepsTitle.setTypeface(browserRemoteStepsTitle.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams browserRemoteStepsTitleParams = matchWidth();
        browserRemoteStepsTitleParams.topMargin = dp(14);
        remoteContent.addView(browserRemoteStepsTitle, browserRemoteStepsTitleParams);

        TextView browserRemoteSteps = text(
                "1. Connect: Put both devices on the same local network. Either device can create a "
                        + "Personal Hotspot and the other joins it, or connect both to the same Wi-Fi.\n\n"
                        + "2. Start: Turn on the switch below, then tap Start floating prompt.\n\n"
                        + "3. Control: On the other device, open a browser, type the address shown in the "
                        + "prompt, then enter its 6-digit code.",
                14,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams browserRemoteStepsParams = matchWidth();
        browserRemoteStepsParams.topMargin = dp(5);
        remoteContent.addView(browserRemoteSteps, browserRemoteStepsParams);

        browserRemoteToggle = new android.widget.Switch(this);
        browserRemoteToggle.setText(AppLanguage.t(this, "Turn on Wi-Fi remote"));
        browserRemoteToggle.setTextColor(Color.WHITE);
        browserRemoteToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        browserRemoteToggle.setPadding(0, dp(8), 0, dp(2));
        browserRemoteToggle.setChecked(preferences.getBoolean("browser_remote", false));
        browserRemoteToggle.setOnCheckedChangeListener((buttonView, checked) -> saveDraft());
        LinearLayout.LayoutParams browserRemoteParams = matchWidth();
        browserRemoteParams.topMargin = dp(6);
        remoteContent.addView(browserRemoteToggle, browserRemoteParams);

        TextView browserRemoteHint = text(
                "The address and code appear only after the prompt starts.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams browserRemoteHintParams = matchWidth();
        browserRemoteHintParams.topMargin = dp(2);
        remoteContent.addView(browserRemoteHint, browserRemoteHintParams);

        Button customBluetoothButton = secondaryButton("Connect custom Prompt Remote (optional)");
        customBluetoothButton.setContentDescription("Set up the optional custom Bluetooth prompt remote");
        customBluetoothButton.setOnClickListener(view -> startActivity(
                new Intent(MainActivity.this, BluetoothRemoteSetupActivity.class)));
        LinearLayout.LayoutParams customBluetoothButtonParams = matchWidth();
        customBluetoothButtonParams.topMargin = dp(10);
        remoteContent.addView(customBluetoothButton, customBluetoothButtonParams);

        formatRow.setOrientation(LinearLayout.HORIZONTAL);
        formatRow.setGravity(Gravity.CENTER_VERTICAL);
        formatRow.setPadding(horizontalPadding, dp(7), horizontalPadding, dp(9));
        formatRow.setBackgroundColor(SCREEN_BACKGROUND);

        Button boldButton = formatButton("B");
        boldButton.setTypeface(boldButton.getTypeface(), Typeface.BOLD);
        boldButton.setContentDescription("Bold selected words");
        boldButton.setOnClickListener(view -> toggleBoldSelection());
        formatRow.addView(boldButton, new LinearLayout.LayoutParams(dp(48), dp(46)));

        Button italicButton = formatButton("I");
        italicButton.setTypeface(italicButton.getTypeface(), Typeface.ITALIC);
        italicButton.setContentDescription("Italicize selected words");
        italicButton.setOnClickListener(view -> toggleItalicSelection());
        LinearLayout.LayoutParams italicParams = new LinearLayout.LayoutParams(dp(48), dp(46));
        italicParams.leftMargin = dp(6);
        formatRow.addView(italicButton, italicParams);

        Button underlineButton = formatButton("U");
        underlineButton.setPaintFlags(underlineButton.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        underlineButton.setContentDescription("Underline selected words");
        underlineButton.setOnClickListener(view -> toggleUnderlineSelection());
        LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(dp(48), dp(46));
        underlineParams.leftMargin = dp(6);
        formatRow.addView(underlineButton, underlineParams);

        addHighlightButton(formatRow, "Y", "Yellow highlight", ScriptFormatting.HIGHLIGHT_YELLOW_COLOR,
                ScriptFormatting.HIGHLIGHT_DARK_TEXT_COLOR);
        addHighlightButton(formatRow, "B", "Blue highlight", ScriptFormatting.HIGHLIGHT_BLUE_COLOR,
                Color.WHITE);
        addHighlightButton(formatRow, "P", "Pink highlight", ScriptFormatting.HIGHLIGHT_PINK_COLOR,
                Color.WHITE);
        addHighlightButton(formatRow, "G", "Green highlight", ScriptFormatting.HIGHLIGHT_GREEN_COLOR,
                ScriptFormatting.HIGHLIGHT_DARK_TEXT_COLOR);

        Button clearButton = formatButton("Clear");
        clearButton.setContentDescription("Clear formatting from selected words");
        clearButton.setOnClickListener(view -> clearFormattingSelection());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(64), dp(46));
        clearParams.leftMargin = dp(6);
        formatRow.addView(clearButton, clearParams);

        // Keep the formatting controls next to the field being edited instead of
        // pinning them over the bottom of the screen.
        bindFormatTarget(scriptTitleInput);
        bindFormatTarget(scriptTagsInput);
        bindFormatTarget(scriptInput);
        showFormatToolbarFor(scriptInput);

        Button startButton = primaryButton("Start floating prompt");
        startButton.setOnClickListener(view -> startPrompt());
        LinearLayout.LayoutParams startParams = matchWidth();
        startParams.topMargin = dp(2);
        promptContent.addView(startButton, startParams);

        TextView speedLabel = text("Auto-scroll speed", 17, Color.WHITE);
        speedLabel.setTypeface(speedLabel.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams speedLabelParams = matchWidth();
        speedLabelParams.topMargin = dp(22);
        promptContent.addView(speedLabel, speedLabelParams);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);

        speedSlider = new SeekBar(this);
        speedSlider.setMax(MAX_SCROLL_SPEED - MIN_SCROLL_SPEED);
        int savedSpeed = preferences.getInt("speed", 45);
        speedSlider.setProgress(clamp(savedSpeed, MIN_SCROLL_SPEED, MAX_SCROLL_SPEED) - MIN_SCROLL_SPEED);

        speedValue = text("", 15, Color.rgb(199, 214, 255));
        speedValue.setGravity(Gravity.CENTER);
        speedValue.setBackground(cardBackground(Color.rgb(37, 43, 58)));
        speedValue.setPadding(dp(8), dp(8), dp(8), dp(8));
        refreshSpeedLabel();

        speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                refreshSpeedLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No action needed.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveDraft();
            }
        });

        speedRow.addView(speedSlider, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams speedValueParams = new LinearLayout.LayoutParams(dp(92), dp(42));
        speedValueParams.leftMargin = dp(10);
        speedRow.addView(speedValue, speedValueParams);
        LinearLayout.LayoutParams speedRowParams = matchWidth();
        speedRowParams.topMargin = dp(5);
        promptContent.addView(speedRow, speedRowParams);

        TextView overlayLabel = text("Floating prompt", 17, Color.WHITE);
        overlayLabel.setTypeface(overlayLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams overlayLabelParams = matchWidth();
        overlayLabelParams.topMargin = dp(22);
        promptContent.addView(overlayLabel, overlayLabelParams);

        TextView overlayHint = text(
                "Set the starting panel here. In Camera, drag the handle near the lens or pinch it to resize.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams overlayHintParams = matchWidth();
        overlayHintParams.topMargin = dp(4);
        promptContent.addView(overlayHint, overlayHintParams);

        SliderSetting textSizeSetting = addSliderSetting(
                promptContent,
                "Prompt text size",
                18,
                72,
                preferences.getInt("text_size", 32),
                "sp");
        textSizeSlider = textSizeSetting.slider;
        textSizeValue = textSizeSetting.value;
        textSizeSlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        SliderSetting opacitySetting = addSliderSetting(
                promptContent,
                "Prompt background",
                0,
                88,
                preferences.getInt("background_opacity", 56),
                "%");
        backgroundOpacitySlider = opacitySetting.slider;
        backgroundOpacityValue = opacitySetting.value;
        backgroundOpacitySlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        SliderSetting heightSetting = addSliderSetting(
                promptContent,
                "Height overlay (max 75%)",
                MIN_HEIGHT_OVERLAY,
                MAX_HEIGHT_OVERLAY,
                preferences.getInt("height_overlay", 70),
                "%");
        heightOverlaySlider = heightSetting.slider;
        heightOverlayValue = heightSetting.value;
        heightOverlaySlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        SliderSetting widthSetting = addSliderSetting(
                promptContent,
                "Width overlay",
                45,
                100,
                preferences.getInt("width_overlay", 100),
                "%");
        widthOverlaySlider = widthSetting.slider;
        widthOverlayValue = widthSetting.value;
        widthOverlaySlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        TextView layoutLabel = text("Reading layout", 17, Color.WHITE);
        layoutLabel.setTypeface(layoutLabel.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams layoutLabelParams = matchWidth();
        layoutLabelParams.topMargin = dp(20);
        promptContent.addView(layoutLabel, layoutLabelParams);

        SliderSetting marginSetting = addSliderSetting(
                promptContent,
                "Reading side margin",
                0,
                20,
                preferences.getInt("side_margin", 5),
                "%");
        sideMarginSlider = marginSetting.slider;
        sideMarginValue = marginSetting.value;
        sideMarginSlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        SliderSetting lineSpacingSetting = addSliderSetting(
                promptContent,
                "Line spacing",
                100,
                200,
                preferences.getInt("line_spacing", 125),
                "%");
        lineSpacingSlider = lineSpacingSetting.slider;
        lineSpacingValue = lineSpacingSetting.value;
        lineSpacingSlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        SliderSetting countdownSetting = addSliderSetting(
                promptContent,
                "Start countdown",
                0,
                10,
                preferences.getInt("start_delay", 3),
                "sec");
        startDelaySlider = countdownSetting.slider;
        startDelayValue = countdownSetting.value;
        startDelaySlider.setOnSeekBarChangeListener(saveOnSliderChangeListener());

        repeatToggle = new android.widget.Switch(this);
        repeatToggle.setText(AppLanguage.t(this, "Repeat silently when prompt ends"));
        repeatToggle.setTextColor(Color.WHITE);
        repeatToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        repeatToggle.setPadding(0, dp(8), 0, dp(8));
        repeatToggle.setChecked(preferences.getBoolean("repeat_prompt", true));
        repeatToggle.setOnCheckedChangeListener((buttonView, checked) -> saveDraft());
        LinearLayout.LayoutParams repeatParams = matchWidth();
        repeatParams.topMargin = dp(10);
        promptContent.addView(repeatToggle, repeatParams);

        Button mirrorButton = secondaryButton("Open mirrored full-screen");
        mirrorButton.setContentDescription("Open reversed text for a physical teleprompter mirror");
        mirrorButton.setOnClickListener(view -> openMirrorMode());
        LinearLayout.LayoutParams mirrorParams = matchWidth();
        mirrorParams.topMargin = dp(10);
        promptContent.addView(mirrorButton, mirrorParams);

        TextView mirrorHint = text(
                "Use only with angled teleprompter glass. The reflected script will read normally.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams mirrorHintParams = matchWidth();
        mirrorHintParams.topMargin = dp(5);
        promptContent.addView(mirrorHint, mirrorHintParams);

        Button permissionButton = secondaryButton("Allow floating prompt");
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOverlayPermission();
            }
        });
        LinearLayout.LayoutParams permissionParams = matchWidth();
        permissionParams.topMargin = dp(10);
        promptContent.addView(permissionButton, permissionParams);

        Button stopButton = secondaryButton("Stop prompt");
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(MainActivity.this, TeleprompterOverlayService.class));
                Toast.makeText(MainActivity.this, "Prompt overlay stopped.", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams stopParams = matchWidth();
        stopParams.topMargin = dp(10);
        promptContent.addView(stopButton, stopParams);

        screenReady = true;
        refreshOverlaySettingLabels();
        return screen;
    }

    private void startPrompt() {
        CharSequence styledScript = scriptInput.getText();
        String plainScript = styledScript.toString();
        if (plainScript.trim().isEmpty()) {
            scriptInput.requestFocus();
            Toast.makeText(this, "Paste a short script first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!TrialAccess.canUsePrompt(preferences)) {
            refreshAccessUi();
            showUnlockDialog(true);
            return;
        }

        saveDraft();
        hideKeyboard();

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow “Appear on top,” then tap Start again.", Toast.LENGTH_LONG).show();
            openOverlayPermission();
            return;
        }

        Intent serviceIntent = new Intent(this, TeleprompterOverlayService.class);
        putPromptExtras(serviceIntent, styledScript);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(
                this,
                "Prompt started. Now open the camera app you prefer.",
                Toast.LENGTH_LONG).show();
    }

    private void openMirrorMode() {
        CharSequence styledScript = scriptInput.getText();
        if (styledScript.toString().trim().isEmpty()) {
            scriptInput.requestFocus();
            Toast.makeText(this, "Paste a short script first.", Toast.LENGTH_SHORT).show();
            return;
        }
        saveDraft();
        hideKeyboard();
        stopService(new Intent(this, TeleprompterOverlayService.class));
        Intent mirrorIntent = new Intent(this, MirrorPrompterActivity.class);
        putPromptExtras(mirrorIntent, styledScript);
        startActivity(mirrorIntent);
    }

    private void putPromptExtras(Intent intent, CharSequence styledScript) {
        intent.putExtra(TeleprompterOverlayService.EXTRA_SCRIPT, styledScript.toString());
        intent.putExtra(
                TeleprompterOverlayService.EXTRA_STYLED_SCRIPT,
                ScriptFormatting.toStoredText(styledScript));
        intent.putExtra(TeleprompterOverlayService.EXTRA_SPEED, currentSpeed());
        intent.putExtra(TeleprompterOverlayService.EXTRA_FONT, selectedFont);
        intent.putExtra(TeleprompterOverlayService.EXTRA_TEXT_COLOR, selectedTextColor);
        intent.putExtra(TeleprompterOverlayService.EXTRA_TEXT_SIZE, currentTextSize());
        intent.putExtra(
                TeleprompterOverlayService.EXTRA_BACKGROUND_OPACITY,
                currentBackgroundOpacity());
        intent.putExtra(
                TeleprompterOverlayService.EXTRA_HEIGHT_OVERLAY,
                currentHeightOverlay());
        intent.putExtra(
                TeleprompterOverlayService.EXTRA_WIDTH_OVERLAY,
                currentWidthOverlay());
        intent.putExtra(TeleprompterOverlayService.EXTRA_SIDE_MARGIN, currentSideMargin());
        intent.putExtra(TeleprompterOverlayService.EXTRA_LINE_SPACING, currentLineSpacing());
        intent.putExtra(TeleprompterOverlayService.EXTRA_TEXT_ALIGNMENT, selectedTextAlignment);
        intent.putExtra(TeleprompterOverlayService.EXTRA_START_DELAY, currentStartDelay());
        intent.putExtra(
                TeleprompterOverlayService.EXTRA_REPEAT,
                repeatToggle != null && repeatToggle.isChecked());
        intent.putExtra(
                TeleprompterOverlayService.EXTRA_BROWSER_REMOTE_ENABLED,
                browserRemoteToggle != null && browserRemoteToggle.isChecked());
    }

    private void refreshAccessUi() {
        if (trialStatus == null || unlockButton == null || preferences == null) {
            return;
        }
        trialStatus.setText(AppLanguage.t(this,
                "Test build · unlimited prompt time · no purchase flow"));
        trialStatus.setTextColor(Color.rgb(149, 221, 194));
        trialStatus.setVisibility(TrialAccess.isTestBuild() ? View.VISIBLE : View.GONE);
        unlockButton.setVisibility(View.GONE);
    }

    private void showTrialExpiryIfRequested(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_TRIAL_EXPIRED, false)) {
            return;
        }
        intent.removeExtra(EXTRA_TRIAL_EXPIRED);
        getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                refreshAccessUi();
                showUnlockDialog(true);
            }
        });
    }

    private void showUnlockDialog(boolean trialComplete) {
        if (TrialAccess.isTestBuild()) {
            Toast.makeText(this, "The test build has unlimited prompt time.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPrivacy() {
        if (BuildConfig.PRO_TEST) { com.costavong.promptoverlay.pro.ProPrivacy.show(this); return; }
        new AlertDialog.Builder(this)
                .setTitle("Privacy")
                .setMessage("Your scripts, titles, tags, and settings stay on this phone. A photo or document you choose to import is processed on-device and is not uploaded or retained as a file. Prompt Overlay does not use your camera, microphone, or account. The optional browser remote communicates only across your local Wi-Fi or hotspot.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void openPlayStoreListing() {
        Intent marketIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + PLAY_PACKAGE_ID));
        marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(marketIntent);
        } catch (ActivityNotFoundException error) {
            Intent webIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + PLAY_PACKAGE_ID));
            startActivity(webIntent);
        }
    }

    private void showRoadmap() {
        new AlertDialog.Builder(this)
                .setTitle("Roadmap")
                .setMessage("This release includes:\n• saved scripts with search, tags, archive, and delete\n• bold, italic, underline, and four highlight colours\n• movable, resizable prompt with independent Height and Width overlay\n• text size, margins, spacing, alignment, countdown, and silent repeat\n• mirrored full-screen reader, imports, and remote controls\n\nPlanned later: AI script assistance, captions, and video editing. The app intentionally keeps camera recording in the camera app you choose.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void chooseOcrImage() {
        Intent imagePicker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        imagePicker.addCategory(Intent.CATEGORY_OPENABLE);
        imagePicker.setType("image/*");
        startActivityForResult(imagePicker, REQUEST_IMPORT_OCR_IMAGE);
    }

    private void showPhoneImportChooser() {
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(4), 0, dp(4), 0);

        TextView prompt = text("Choose how you want to import your script:",
                15, Color.rgb(176, 190, 214));
        choices.addView(prompt, matchWidth());

        Button documentChoice = secondaryButton(
                "Document / file\nWord, PDF, text, Markdown, or HTML");
        documentChoice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        documentChoice.setPadding(dp(16), dp(8), dp(16), dp(8));
        documentChoice.setMinHeight(dp(72));
        documentChoice.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams documentChoiceParams = matchWidth();
        documentChoiceParams.topMargin = dp(12);
        choices.addView(documentChoice, documentChoiceParams);

        Button photoChoice = secondaryButton(
                "Photo / screenshot\nExtract text on-device with OCR");
        photoChoice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        photoChoice.setPadding(dp(16), dp(8), dp(16), dp(8));
        photoChoice.setMinHeight(dp(72));
        photoChoice.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams photoChoiceParams = matchWidth();
        photoChoiceParams.topMargin = dp(10);
        choices.addView(photoChoice, photoChoiceParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Import script")
                .setView(choices)
                .setNegativeButton("Cancel", null)
                .create();
        documentChoice.setOnClickListener(view -> {
            dialog.dismiss();
            chooseDocument();
        });
        photoChoice.setOnClickListener(view -> {
            dialog.dismiss();
            chooseOcrImage();
        });
        dialog.show();
    }

    private void chooseDocument() {
        Intent documentPicker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        documentPicker.addCategory(Intent.CATEGORY_OPENABLE);
        documentPicker.setType("*/*");
        addSupportedDocumentTypes(documentPicker);
        startActivityForResult(documentPicker, REQUEST_IMPORT_DOCUMENT);
    }

    private void chooseGoogleDriveDocument() {
        // Use Drive's own picker activity. Querying its document-provider roots
        // is restricted on some phones and can falsely report that an installed,
        // signed-in Drive account is unavailable.
        Intent googleDrivePicker = new Intent(Intent.ACTION_GET_CONTENT);
        googleDrivePicker.addCategory(Intent.CATEGORY_OPENABLE);
        googleDrivePicker.setType("*/*");
        addSupportedDocumentTypes(googleDrivePicker);
        googleDrivePicker.setPackage("com.google.android.apps.docs");
        try {
            startActivityForResult(googleDrivePicker, REQUEST_IMPORT_GOOGLE_DRIVE_DOCUMENT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this,
                    "Google Drive could not open. Install or update Google Drive, then sign in and try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addSupportedDocumentTypes(Intent intent) {
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "text/markdown",
                "text/html"
        });
    }

    private void importDocument(Uri documentUri) {
        String displayName = DocumentTextExtractor.displayName(this, documentUri);
        DocumentTextExtractor.Type type = DocumentTextExtractor.typeFor(this, documentUri, displayName);
        if (type == DocumentTextExtractor.Type.UNSUPPORTED) {
            Toast.makeText(this,
                    "Choose a Word (.docx), PDF, TXT, Markdown, or HTML document.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (type == DocumentTextExtractor.Type.PDF) {
            importPdfDocument(documentUri, displayName);
            return;
        }
        final String finalDisplayName = displayName;
        Toast.makeText(this, "Reading " + displayName + " on this phone…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String documentText = DocumentTextExtractor.read(
                            MainActivity.this,
                            documentUri,
                            type);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            applyDocumentText(finalDisplayName, documentText, false);
                        }
                    });
                } catch (java.io.IOException error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Could not read that document. Try a smaller DOCX, TXT, Markdown, or HTML file.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void importPdfDocument(Uri documentUri, String displayName) {
        if (textRecognizer == null) {
            Toast.makeText(this, "Document import is not ready. Please reopen the app.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalDisplayName = displayName;
        Toast.makeText(this, "Preparing PDF on this phone…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PdfImportSession session = PdfImportSession.open(MainActivity.this, documentUri);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closePdfImport();
                            activePdfImport = session;
                            Toast.makeText(MainActivity.this,
                                    "Reading " + session.pageCount() + " PDF page"
                                            + (session.pageCount() == 1 ? "" : "s") + " on this phone…",
                                    Toast.LENGTH_LONG).show();
                            readNextPdfPage(session, finalDisplayName);
                        }
                    });
                } catch (java.io.IOException error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Could not open that PDF. Try exporting it as a DOCX or text file.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void readNextPdfPage(PdfImportSession session, String displayName) {
        if (activePdfImport != session) {
            return;
        }
        if (!session.hasNextPage()) {
            finishPdfImport(session, displayName);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap pageBitmap = session.renderNextPage();
                    int pageNumber = session.renderedPageCount();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (activePdfImport != session || textRecognizer == null) {
                                pageBitmap.recycle();
                                return;
                            }
                            InputImage image = InputImage.fromBitmap(pageBitmap, 0);
                            textRecognizer.process(image)
                                    .addOnSuccessListener(result -> {
                                        pageBitmap.recycle();
                                        session.append(result.getText());
                                        readNextPdfPage(session, displayName);
                                    })
                                    .addOnFailureListener(error -> {
                                        pageBitmap.recycle();
                                        session.noteUnreadablePage(pageNumber);
                                        readNextPdfPage(session, displayName);
                                    });
                        }
                    });
                } catch (java.io.IOException error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (activePdfImport == session) {
                                closePdfImport();
                                Toast.makeText(MainActivity.this,
                                        "Could not read that PDF page. Try exporting the document as DOCX or text.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void finishPdfImport(PdfImportSession session, String displayName) {
        if (activePdfImport != session) {
            return;
        }
        String importedText = session.text();
        String unreadableNote = session.unreadablePageNote();
        closePdfImport();
        if (importedText.trim().isEmpty()) {
            Toast.makeText(this,
                    "No readable text was found in that PDF. Try a sharper PDF or export it as DOCX.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        applyDocumentText(displayName, importedText, true);
        if (!unreadableNote.isEmpty()) {
            Toast.makeText(this, unreadableNote, Toast.LENGTH_LONG).show();
        }
    }

    private void importOcrImage(Uri imageUri) {
        if (textRecognizer == null) {
            Toast.makeText(this, "OCR is not ready. Please reopen the app.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            Toast.makeText(this, "Reading text from the image…", Toast.LENGTH_SHORT).show();
            textRecognizer.process(image)
                    .addOnSuccessListener(result -> applyOcrText(result.getText()))
                    .addOnFailureListener(error -> Toast.makeText(
                            MainActivity.this,
                            "Could not read text from that image. Try a sharper image with larger text.",
                            Toast.LENGTH_LONG).show());
        } catch (java.io.IOException error) {
            Toast.makeText(this, "Could not open that image.", Toast.LENGTH_LONG).show();
        }
    }

    private void applyOcrText(String recognizedText) {
        applyImportedText(
                "OCR text ready",
                "Replace your current script, or add the recognized text below it? Please proofread before recording.",
                recognizedText,
                "Text imported. Please proofread it before recording.");
    }

    private void applyDocumentText(String displayName, String documentText, boolean fromPdf) {
        String reviewNote = fromPdf
                ? "PDF text imported. Please proofread it before recording."
                : "Document text imported. Review the line breaks before recording.";
        applyImportedText(
                "Document ready",
                "Import text from “" + displayName
                        + "” by replacing your current script, or adding it below?"
                        + (fromPdf ? " Please proofread PDF text before recording." : ""),
                documentText,
                reviewNote);
    }

    private void applyImportedText(String title, String message, String importedText, String successMessage) {
        String cleanText = importedText == null ? "" : importedText.trim();
        if (cleanText.isEmpty()) {
            Toast.makeText(this, "No readable text was found in that file.", Toast.LENGTH_LONG).show();
            return;
        }
        String existingText = scriptInput == null ? "" : scriptInput.getText().toString().trim();
        if (existingText.isEmpty()) {
            scriptInput.setText(cleanText);
            saveDraft();
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Add below", (dialog, which) -> {
                    scriptInput.append("\n\n" + cleanText);
                    saveDraft();
                })
                .setPositiveButton("Replace", (dialog, which) -> {
                    scriptInput.setText(cleanText);
                    saveDraft();
                })
                .show();
    }

    private void closePdfImport() {
        if (activePdfImport != null) {
            activePdfImport.close();
            activePdfImport = null;
        }
    }

    private static final class PdfImportSession {
        private final ParcelFileDescriptor fileDescriptor;
        private final PdfRenderer renderer;
        private final int pageCount;
        private final StringBuilder importedText = new StringBuilder();
        private int renderedPageCount;
        private int unreadablePageCount;

        private PdfImportSession(ParcelFileDescriptor fileDescriptor, PdfRenderer renderer, int pageCount) {
            this.fileDescriptor = fileDescriptor;
            this.renderer = renderer;
            this.pageCount = pageCount;
        }

        static PdfImportSession open(Context context, Uri uri) throws java.io.IOException {
            ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            if (fileDescriptor == null) {
                throw new java.io.IOException("Could not open the PDF.");
            }
            try {
                PdfRenderer renderer = new PdfRenderer(fileDescriptor);
                int availablePages = renderer.getPageCount();
                if (availablePages < 1) {
                    renderer.close();
                    fileDescriptor.close();
                    throw new java.io.IOException("The PDF has no pages.");
                }
                return new PdfImportSession(
                        fileDescriptor,
                        renderer,
                        Math.min(availablePages, MAX_PDF_IMPORT_PAGES));
            } catch (RuntimeException error) {
                fileDescriptor.close();
                throw new java.io.IOException("Could not open the PDF.", error);
            }
        }

        boolean hasNextPage() {
            return renderedPageCount < pageCount;
        }

        int pageCount() {
            return pageCount;
        }

        int renderedPageCount() {
            return renderedPageCount;
        }

        Bitmap renderNextPage() throws java.io.IOException {
            if (!hasNextPage()) {
                throw new java.io.IOException("No PDF pages remain.");
            }
            PdfRenderer.Page page = null;
            try {
                page = renderer.openPage(renderedPageCount);
                float scale = Math.min(2f, 1440f / Math.max(1, page.getWidth()));
                int width = Math.max(720, Math.round(page.getWidth() * scale));
                int height = Math.max(720, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                renderedPageCount++;
                return bitmap;
            } catch (RuntimeException error) {
                throw new java.io.IOException("Could not render the PDF page.", error);
            } finally {
                if (page != null) {
                    page.close();
                }
            }
        }

        void append(String pageText) {
            String cleanPageText = pageText == null ? "" : pageText.trim();
            if (cleanPageText.isEmpty()) {
                return;
            }
            if (importedText.length() > 0) {
                importedText.append("\n\n");
            }
            importedText.append(cleanPageText);
        }

        void noteUnreadablePage(int pageNumber) {
            unreadablePageCount++;
        }

        String text() {
            return importedText.toString();
        }

        String unreadablePageNote() {
            if (unreadablePageCount == 0) {
                return "";
            }
            return unreadablePageCount == 1
                    ? "One PDF page could not be read. Please review the imported script."
                    : unreadablePageCount + " PDF pages could not be read. Please review the imported script.";
        }

        void close() {
            try {
                renderer.close();
            } catch (Exception ignored) {
                // Nothing else can use a completed or cancelled import.
            }
            try {
                fileDescriptor.close();
            } catch (java.io.IOException ignored) {
                // Nothing else can use a completed or cancelled import.
            }
        }
    }

    private void openOverlayPermission() {
        Intent settingsIntent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(settingsIntent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(SCREEN_BACKGROUND);
        getWindow().setNavigationBarColor(SCREEN_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().setNavigationBarDividerColor(SCREEN_BACKGROUND);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int systemUiFlags = getWindow().getDecorView().getSystemUiVisibility();
            systemUiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            systemUiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
        }
    }

    private void saveDraft() {
        if (!screenReady || scriptInput == null || speedSlider == null) {
            return;
        }
        preferences.edit()
                .putString("script", scriptInput.getText().toString())
                .putString("script_format", ScriptFormatting.toStoredText(scriptInput.getText()))
                .putInt("speed", currentSpeed())
                .putString("font", selectedFont)
                .putString("text_color", selectedTextColor)
                .putString("text_alignment", selectedTextAlignment)
                .putInt("text_size", currentTextSize())
                .putInt("background_opacity", currentBackgroundOpacity())
                .putInt("height_overlay", currentHeightOverlay())
                .putInt("width_overlay", currentWidthOverlay())
                .putInt("side_margin", currentSideMargin())
                .putInt("line_spacing", currentLineSpacing())
                .putInt("start_delay", currentStartDelay())
                .putBoolean("repeat_prompt", repeatToggle != null && repeatToggle.isChecked())
                .putBoolean("browser_remote", browserRemoteToggle != null && browserRemoteToggle.isChecked())
                .apply();
        persistCurrentScriptToLibrary(false);
    }

    private void persistCurrentScriptToLibrary(boolean forceCreate) {
        if (scriptInput == null || scriptTitleInput == null || scriptTagsInput == null) {
            return;
        }
        String plainText = scriptInput.getText().toString().trim();
        if (plainText.isEmpty()) {
            return;
        }
        if (currentScriptId == null || currentScriptId.trim().isEmpty()) {
            if (!forceCreate && !screenReady) {
                return;
            }
            currentScriptId = ScriptLibrary.newId();
            preferences.edit().putString("current_script_id", currentScriptId).apply();
        }

        ScriptLibrary.Entry existing = ScriptLibrary.find(preferences, currentScriptId);
        String title = scriptTitleInput.getText().toString().trim();
        if (title.isEmpty()) {
            title = ScriptLibrary.firstLine(plainText);
        }
        if (title.isEmpty()) {
            title = "Untitled script";
        }
        String tags = scriptTagsInput.getText().toString().trim();
        String storedTitle = scriptTitleInput.getText().toString().trim().isEmpty()
                ? ScriptFormatting.toStoredText(title)
                : ScriptFormatting.toStoredText(scriptTitleInput.getText());
        String storedTags = ScriptFormatting.toStoredText(scriptTagsInput.getText());
        ScriptLibrary.Entry entry = new ScriptLibrary.Entry(
                currentScriptId,
                title,
                tags,
                storedTitle,
                storedTags,
                ScriptFormatting.toStoredText(scriptInput.getText()),
                existing != null && existing.archived,
                System.currentTimeMillis());
        ScriptLibrary.upsert(preferences, entry);
        if (scriptTitleInput.getText().toString().trim().isEmpty()) {
            loadingScript = true;
            scriptTitleInput.setText(title);
            loadingScript = false;
        }
    }

    private void saveCurrentScript(boolean showMessage) {
        if (scriptInput == null || scriptInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, AppLanguage.t(this, "Write a script before saving it."), Toast.LENGTH_SHORT).show();
            return;
        }
        persistCurrentScriptToLibrary(true);
        if (showMessage) {
            Toast.makeText(this, AppLanguage.t(this, "Script saved on this phone."), Toast.LENGTH_SHORT).show();
        }
    }

    private void startNewScript() {
        persistCurrentScriptToLibrary(false);
        loadingScript = true;
        currentScriptId = null;
        scriptTitleInput.setText("");
        scriptTagsInput.setText("");
        scriptInput.setText("");
        loadingScript = false;
        preferences.edit().remove("current_script_id").apply();
        scriptTitleInput.requestFocus();
        Toast.makeText(this, AppLanguage.t(this, "New script ready."), Toast.LENGTH_SHORT).show();
    }

    private void openSavedScript(String id) {
        ScriptLibrary.Entry entry = ScriptLibrary.find(preferences, id);
        if (entry == null) {
            return;
        }
        persistCurrentScriptToLibrary(false);
        loadingScript = true;
        currentScriptId = entry.id;
        preferences.edit().putString("current_script_id", currentScriptId).apply();
        scriptTitleInput.setText(ScriptFormatting.fromStoredText(entry.storedTitle));
        scriptTagsInput.setText(ScriptFormatting.fromStoredText(entry.storedTags));
        scriptInput.setText(ScriptFormatting.fromStoredText(entry.storedText));
        loadingScript = false;
        Toast.makeText(this, "Opened “" + entry.title + "”.", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout createFormatToolbarHost() {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setVisibility(View.GONE);
        return host;
    }

    private void bindFormatTarget(final EditText input) {
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    showFormatToolbarFor(input);
                }
            }
        });
    }

    private void showFormatToolbarFor(EditText input) {
        if (formatToolbar == null || input == null) {
            return;
        }
        formatTarget = input;
        LinearLayout destination;
        if (input == scriptTitleInput) {
            destination = titleFormatToolbarHost;
        } else if (input == scriptTagsInput) {
            destination = tagsFormatToolbarHost;
        } else {
            destination = scriptFormatToolbarHost;
        }
        if (destination == null) {
            return;
        }

        if (titleFormatToolbarHost != null) {
            titleFormatToolbarHost.setVisibility(destination == titleFormatToolbarHost
                    ? View.VISIBLE : View.GONE);
        }
        if (tagsFormatToolbarHost != null) {
            tagsFormatToolbarHost.setVisibility(destination == tagsFormatToolbarHost
                    ? View.VISIBLE : View.GONE);
        }
        if (scriptFormatToolbarHost != null) {
            scriptFormatToolbarHost.setVisibility(destination == scriptFormatToolbarHost
                    ? View.VISIBLE : View.GONE);
        }

        if (formatToolbar.getParent() != destination) {
            if (formatToolbar.getParent() instanceof ViewGroup) {
                ((ViewGroup) formatToolbar.getParent()).removeView(formatToolbar);
            }
            destination.addView(formatToolbar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(62)));
        }
    }

    private EditText currentFormatTarget() {
        return formatTarget == null ? scriptInput : formatTarget;
    }

    private void toggleBoldSelection() {
        EditText target = currentFormatTarget();
        int[] selection = currentSelection(target);
        if (selection == null) {
            return;
        }
        ScriptFormatting.toggleBold(target.getText(), selection[0], selection[1]);
        restoreSelection(target, selection[0], selection[1]);
        saveDraft();
    }

    private void toggleUnderlineSelection() {
        EditText target = currentFormatTarget();
        int[] selection = currentSelection(target);
        if (selection == null) {
            return;
        }
        ScriptFormatting.toggleUnderline(target.getText(), selection[0], selection[1]);
        restoreSelection(target, selection[0], selection[1]);
        saveDraft();
    }

    private void toggleItalicSelection() {
        EditText target = currentFormatTarget();
        int[] selection = currentSelection(target);
        if (selection == null) {
            return;
        }
        ScriptFormatting.toggleItalic(target.getText(), selection[0], selection[1]);
        restoreSelection(target, selection[0], selection[1]);
        saveDraft();
    }

    private void toggleHighlightSelection() {
        EditText target = currentFormatTarget();
        int[] selection = currentSelection(target);
        if (selection == null) {
            return;
        }
        ScriptFormatting.toggleHighlight(target.getText(), selection[0], selection[1]);
        restoreSelection(target, selection[0], selection[1]);
        saveDraft();
    }

    private void toggleHighlightSelection(int backgroundColor, int textColor) {
        EditText target = currentFormatTarget();
        int[] selection = currentSelection(target);
        if (selection == null) {
            return;
        }
        ScriptFormatting.toggleHighlight(
                target.getText(), selection[0], selection[1], backgroundColor, textColor);
        restoreSelection(target, selection[0], selection[1]);
        saveDraft();
    }

    private void clearFormattingSelection() {
        EditText target = currentFormatTarget();
        int[] selection = currentSelection(target);
        if (selection == null) {
            return;
        }
        ScriptFormatting.clearFormatting(target.getText(), selection[0], selection[1]);
        restoreSelection(target, selection[0], selection[1]);
        saveDraft();
    }

    private void addHighlightButton(
            LinearLayout row,
            String label,
            String description,
            int backgroundColor,
            int textColor) {
        Button button = formatButton(label);
        button.setTextColor(textColor);
        button.setBackground(cardBackground(backgroundColor));
        button.setContentDescription(description);
        button.setOnClickListener(view -> toggleHighlightSelection(backgroundColor, textColor));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(46));
        params.leftMargin = dp(6);
        row.addView(button, params);
    }

    private int[] currentSelection(EditText target) {
        int start = Math.min(target.getSelectionStart(), target.getSelectionEnd());
        int end = Math.max(target.getSelectionStart(), target.getSelectionEnd());
        if (start < 0 || end <= start) {
            Toast.makeText(this, "Select the words you want to style first.", Toast.LENGTH_SHORT).show();
            return null;
        }
        return new int[]{start, end};
    }

    private void restoreSelection(final EditText target, int start, int end) {
        target.requestFocus();
        target.setSelection(start, end);
        target.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputManager =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputManager != null) {
                    inputManager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    private int currentSpeed() {
        return MIN_SCROLL_SPEED + speedSlider.getProgress();
    }

    private int currentTextSize() {
        return 18 + (textSizeSlider == null ? 14 : textSizeSlider.getProgress());
    }

    private int currentBackgroundOpacity() {
        return backgroundOpacitySlider == null ? 56 : backgroundOpacitySlider.getProgress();
    }

    private int currentHeightOverlay() {
        return MIN_HEIGHT_OVERLAY + (heightOverlaySlider == null ? 35 : heightOverlaySlider.getProgress());
    }

    private int currentWidthOverlay() {
        return 45 + (widthOverlaySlider == null ? 55 : widthOverlaySlider.getProgress());
    }

    private int currentSideMargin() {
        return sideMarginSlider == null ? 5 : sideMarginSlider.getProgress();
    }

    private int currentLineSpacing() {
        return 100 + (lineSpacingSlider == null ? 25 : lineSpacingSlider.getProgress());
    }

    private int currentStartDelay() {
        return startDelaySlider == null ? 3 : startDelaySlider.getProgress();
    }

    private void refreshSpeedLabel() {
        int speed = currentSpeed();
        String pace = speed <= 30 ? "slow" : speed <= 60 ? "medium" : "fast";
        speedValue.setText(speed + " px/s\n" + AppLanguage.t(this, pace));
    }

    private void refreshOverlaySettingLabels() {
        if (textSizeValue != null) {
            textSizeValue.setText(currentTextSize() + " sp");
        }
        if (backgroundOpacityValue != null) {
            backgroundOpacityValue.setText(currentBackgroundOpacity() + "%");
        }
        if (heightOverlayValue != null) {
            heightOverlayValue.setText(currentHeightOverlay() + "%");
        }
        if (widthOverlayValue != null) {
            widthOverlayValue.setText(currentWidthOverlay() + "%");
        }
        if (sideMarginValue != null) {
            sideMarginValue.setText(currentSideMargin() + "%");
        }
        if (lineSpacingValue != null) {
            lineSpacingValue.setText(String.format(java.util.Locale.US, "%.2f x", currentLineSpacing() / 100f));
        }
        if (startDelayValue != null) {
            int seconds = currentStartDelay();
            startDelayValue.setText(seconds == 0
                    ? AppLanguage.t(this, "Off")
                    : seconds + " sec");
        }
    }

    private SeekBar.OnSeekBarChangeListener saveOnSliderChangeListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                refreshOverlaySettingLabels();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No action needed.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveDraft();
            }
        };
    }

    private void selectFont(String font) {
        selectedFont = PromptFont.normalize(font);
        if (scriptInput != null) {
            scriptInput.setTypeface(PromptFont.typefaceFor(selectedFont));
        }
        saveDraft();
    }

    private void selectTextColor(String color) {
        selectedTextColor = PromptTextColor.normalize(color);
        if (scriptInput != null) {
            scriptInput.setTextColor(PromptTextColor.colorFor(selectedTextColor));
        }
        saveDraft();
    }

    private void selectTextAlignment(String alignment) {
        selectedTextAlignment = PromptTextAlignment.normalize(alignment);
        saveDraft();
    }

    private SectionGroup addSection(LinearLayout column, String title, boolean initiallyExpanded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(Color.rgb(29, 35, 49)));
        card.setPadding(dp(14), dp(10), dp(14), dp(14));

        LinearLayout.LayoutParams cardParams = matchWidth();
        cardParams.topMargin = dp(12);
        column.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setFocusable(true);
        header.setMinimumHeight(dp(42));

        TextView titleView = text(title, 17, Color.WHITE);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView indicator = text(initiallyExpanded ? "▾" : "▸", 22, Color.rgb(149, 221, 194));
        indicator.setGravity(Gravity.CENTER);
        header.addView(indicator, new LinearLayout.LayoutParams(dp(36), dp(42)));
        card.addView(header, matchWidth());

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(58, 68, 88));
        card.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, 0);
        card.addView(content, matchWidth());

        SectionGroup group = new SectionGroup(AppLanguage.t(this, title), header, indicator, content);
        header.setOnClickListener(view -> setSectionExpanded(
                group, content.getVisibility() != View.VISIBLE));
        setSectionExpanded(group, initiallyExpanded);
        return group;
    }

    private void setSectionExpanded(SectionGroup group, boolean expanded) {
        group.content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        group.indicator.setText(expanded ? "▾" : "▸");
        group.header.setContentDescription(
                (expanded ? "Collapse " : "Expand ") + group.title + " section");
    }

    private static final class SectionGroup {
        final String title;
        final View header;
        final TextView indicator;
        final LinearLayout content;

        SectionGroup(String title, LinearLayout header, TextView indicator, LinearLayout content) {
            this.title = title;
            this.header = header;
            this.indicator = indicator;
            this.content = content;
        }
    }

    private Spinner styledSpinner(String[] items) {
        String[] translatedItems = new String[items.length];
        for (int index = 0; index < items.length; index++) {
            translatedItems[index] = AppLanguage.t(this, items[index]);
        }
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                translatedItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(cardBackground(Color.rgb(45, 51, 68)));
        spinner.setPadding(dp(12), 0, dp(12), 0);
        return spinner;
    }

    private String fontAt(int index) {
        switch (index) {
            case 1: return PromptFont.CLASSIC;
            case 2: return PromptFont.MONO;
            case 3: return PromptFont.COMPACT;
            default: return PromptFont.CLEAN;
        }
    }

    private int fontIndex(String font) {
        String normalized = PromptFont.normalize(font);
        if (PromptFont.CLASSIC.equals(normalized)) return 1;
        if (PromptFont.MONO.equals(normalized)) return 2;
        if (PromptFont.COMPACT.equals(normalized)) return 3;
        return 0;
    }

    private void hideKeyboard() {
        InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            inputManager.hideSoftInputFromWindow(scriptInput.getWindowToken(), 0);
        }
        scriptInput.clearFocus();
    }

    private SliderSetting addSliderSetting(
            LinearLayout column,
            String label,
            int minimum,
            int maximum,
            int savedValue,
            String suffix) {
        TextView title = text(label, 15, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWidth();
        titleParams.topMargin = dp(14);
        column.addView(title, titleParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum - minimum);
        int safeValue = clamp(savedValue, minimum, maximum);
        slider.setProgress(safeValue - minimum);
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView value = text(safeValue + " " + suffix, 14, Color.rgb(199, 214, 255));
        value.setGravity(Gravity.CENTER);
        value.setBackground(cardBackground(Color.rgb(37, 43, 58)));
        value.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(76), dp(38));
        valueParams.leftMargin = dp(10);
        row.addView(value, valueParams);

        column.addView(row, matchWidth());
        return new SliderSetting(slider, value);
    }

    private static final class SliderSetting {
        final SeekBar slider;
        final TextView value;

        SliderSetting(SeekBar slider, TextView value) {
            this.slider = slider;
            this.value = value;
        }
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(AppLanguage.t(this, value));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1f);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(AppLanguage.t(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        button.setTextColor(Color.WHITE);
        button.setBackground(cardBackground(Color.rgb(76, 104, 238)));
        button.setMinHeight(dp(54));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(AppLanguage.t(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTextColor(Color.rgb(218, 225, 238));
        button.setBackground(cardBackground(Color.rgb(45, 51, 68)));
        button.setMinHeight(dp(48));
        return button;
    }

    private Button smallSecondaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(AppLanguage.t(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTextColor(Color.rgb(218, 225, 238));
        button.setBackground(cardBackground(Color.rgb(45, 51, 68)));
        button.setMinHeight(dp(40));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private Button formatButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(AppLanguage.t(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground(cardBackground(Color.rgb(45, 51, 68)));
        button.setMinHeight(dp(46));
        button.setMinWidth(0);
        return button;
    }

    private GradientDrawable cardBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
