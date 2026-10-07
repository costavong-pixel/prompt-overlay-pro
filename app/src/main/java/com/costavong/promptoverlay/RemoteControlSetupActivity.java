package com.costavong.promptoverlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Configures common Bluetooth/USB keyboard, clicker, pedal, and gamepad remotes. */
public class RemoteControlSetupActivity extends Activity {
    private static final int SCREEN_BACKGROUND = Color.rgb(14, 18, 27);
    private static final int CARD_BACKGROUND = Color.rgb(27, 34, 49);

    private SharedPreferences preferences;
    private LinearLayout mappings;
    private TextView status;
    private String listeningForAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("prompt_overlay", MODE_PRIVATE);
        setContentView(createScreen());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (listeningForAction != null && RemoteControlProfile.canAssign(event)) {
            RemoteControlProfile.setKey(preferences, listeningForAction, event.getKeyCode());
            status.setText(RemoteControlProfile.actionLabel(listeningForAction)
                    + " is now set to " + RemoteControlProfile.keyLabel(event.getKeyCode()) + ".");
            listeningForAction = null;
            renderMappings();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private View createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(SCREEN_BACKGROUND);
        scroll.setLayoutDirection(AppLanguage.layoutDirection(this));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = label("Bluetooth & hardware remote", 25, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        column.addView(title, matchWidth());

        TextView description = label(
                "Pair a Bluetooth remote, wireless keyboard, presentation clicker, foot pedal, "
                        + "or gamepad in Android Settings first. Standard keys work immediately. "
                        + "Use Set button for any different key your remote sends.",
                15,
                Color.rgb(205, 216, 241));
        LinearLayout.LayoutParams descriptionParams = matchWidth();
        descriptionParams.topMargin = dp(10);
        column.addView(description, descriptionParams);

        TextView limitation = label(
                "This supports standard Android keyboard, media, and gamepad commands. "
                        + "A brand-specific remote that talks only to its own app needs that brand’s protocol.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams limitationParams = matchWidth();
        limitationParams.topMargin = dp(8);
        column.addView(limitation, limitationParams);

        status = label("Choose an action, then press one button on the paired remote.",
                14, Color.rgb(255, 217, 120));
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackground(cardBackground(Color.rgb(59, 49, 28)));
        LinearLayout.LayoutParams statusParams = matchWidth();
        statusParams.topMargin = dp(18);
        column.addView(status, statusParams);

        TextView mappingsTitle = label("Remote controls", 18, Color.WHITE);
        mappingsTitle.setTypeface(mappingsTitle.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams mappingsTitleParams = matchWidth();
        mappingsTitleParams.topMargin = dp(20);
        column.addView(mappingsTitle, mappingsTitleParams);

        mappings = new LinearLayout(this);
        mappings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mappingsParams = matchWidth();
        mappingsParams.topMargin = dp(8);
        column.addView(mappings, mappingsParams);

        Button reset = secondaryButton("Reset to standard controls");
        reset.setOnClickListener(view -> {
            RemoteControlProfile.clearCustomKeys(preferences);
            listeningForAction = null;
            status.setText("Standard remote controls restored.");
            renderMappings();
        });
        LinearLayout.LayoutParams resetParams = matchWidth();
        resetParams.topMargin = dp(18);
        column.addView(reset, resetParams);

        Button done = primaryButton("Done");
        done.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams doneParams = matchWidth();
        doneParams.topMargin = dp(10);
        column.addView(done, doneParams);

        renderMappings();
        return scroll;
    }

    private void renderMappings() {
        if (mappings == null) {
            return;
        }
        mappings.removeAllViews();
        for (String action : RemoteControlProfile.ACTIONS) {
            mappings.addView(createMappingCard(action));
        }
    }

    private View createMappingCard(String action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardBackground(CARD_BACKGROUND));

        TextView actionName = label(RemoteControlProfile.actionLabel(action), 16, Color.WHITE);
        actionName.setTypeface(actionName.getTypeface(), Typeface.BOLD);
        card.addView(actionName, matchWidth());

        int customKey = RemoteControlProfile.customKeyFor(preferences, action);
        String mapping = customKey >= 0
                ? "Set to: " + RemoteControlProfile.keyLabel(customKey)
                : "Default: " + RemoteControlProfile.defaultHint(action);
        TextView mappingText = label(mapping, 13, Color.rgb(192, 207, 235));
        LinearLayout.LayoutParams mappingParams = matchWidth();
        mappingParams.topMargin = dp(4);
        card.addView(mappingText, mappingParams);

        Button set = secondaryButton(listeningForAction != null && listeningForAction.equals(action)
                ? "Listening — press remote button"
                : "Set button");
        set.setOnClickListener(view -> {
            listeningForAction = action;
            status.setText("Listening for “" + RemoteControlProfile.actionLabel(action)
                    + "”. Press the button on your remote now.");
            renderMappings();
        });
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        setParams.topMargin = dp(9);
        card.addView(set, setParams);

        LinearLayout.LayoutParams cardParams = matchWidth();
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        return card;
    }

    private TextView label(String value, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(AppLanguage.t(this, value));
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return text;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(AppLanguage.t(this, value));
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setBackground(cardBackground(Color.rgb(30, 105, 180)));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(AppLanguage.t(this, value));
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackground(cardBackground(Color.rgb(48, 60, 82)));
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
        background.setCornerRadius(dp(14));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
