package com.costavong.promptoverlay.pro;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.costavong.promptoverlay.AppLanguage;
import com.costavong.promptoverlay.BuildConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * User-controlled, private product feedback. No background uploads: the user
 * explicitly selects evidence, reviews the email and sends it in a mail app.
 */
public final class ProFeedbackActivity extends Activity {
    private static final int PICK_MEDIA=791;
    private static final int BG=0xff131720,CARD=0xff202634,INPUT=0xff2b3344,
            ACCENT=0xff4b67ed,TEAL=0xff8bd4c5,WHITE=0xfff3f5fb;
    private final LinkedHashMap<String,Evidence> evidence=new LinkedHashMap<>();

    private Spinner topic;
    private EditText tried,outcome,steps,link;
    private LinearLayout mediaList;
    private TextView mediaSummary;

    private static final class Evidence {
        final Uri uri;
        final String label,mime;
        final long size;
        Evidence(Uri uri,String label,String mime,long size){
            this.uri=uri;this.label=label;this.mime=mime;this.size=size;
        }
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String tr(String key){return ProStrings.t(this,key);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        return d;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private TextView label(String raw,int size,boolean bold){
        TextView t=new TextView(this);t.setText(tr(raw));t.setTextColor(WHITE);t.setTextSize(size);
        t.setPadding(0,dp(5),0,dp(5));
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private Button button(String key,int color,Runnable action){
        Button b=new Button(this);b.setText(tr(key));b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackground(shape(color,14));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(10),0,dp(10),0);
        b.setOnClickListener(v->action.run());return b;
    }
    private void full(LinearLayout parent,View view,int gapAfter){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(4),0,dp(gapAfter));parent.addView(view,lp);
    }
    private EditText field(LinearLayout parent,String heading,String hint,int minLines){
        parent.addView(label(heading,14,true));
        EditText e=new EditText(this);e.setTextColor(WHITE);
        e.setHintTextColor(0xffa8b3c6);e.setTextSize(14);
        e.setHint(tr(hint));e.setBackground(shape(INPUT,12));
        e.setPadding(dp(12),dp(9),dp(12),dp(9));
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(minLines);e.setMaxLines(Math.max(minLines,6));
        full(parent,e,10);return e;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(true);
        LinearLayout root=column();root.setPadding(dp(18),dp(12),dp(18),dp(28));
        root.setLayoutDirection(AppLanguage.layoutDirection(this));
        root.setFocusableInTouchMode(true);scroll.addView(root);setContentView(scroll);

        full(root,button("Back",INPUT,this::finish),10);
        root.addView(label("Win a FREE Lifetime License from Us",22,true));

        TextView promise=label("We will choose 500 users to receive a FREE Pro Lifetime license, based on the quality of their private product feedback.",14,false);
        full(root,promise,5);
        TextView quality=label("Only detailed, specific, useful feedback will be considered. Explain what you tried, what happened, and how to reproduce the issue or improve the feature. Photos, short videos, and links are welcome.",13,false);
        quality.setTextColor(0xffc7d0e0);full(root,quality,4);
        TextView independent=label("Public Google Play ratings or reviews are not required and do not affect selection. Submitting feedback does not guarantee selection.",12,false);
        independent.setTextColor(TEAL);full(root,independent,12);

        root.addView(label("Feature you tried",14,true));
        String[] keys={"Teleprompter","Video editor","Captions","Export","Usability","Other"};
        String[] names=new String[keys.length];for(int i=0;i<keys.length;i++)names[i]=tr(keys[i]);
        topic=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);
        topic.setAdapter(adapter);topic.setBackground(shape(INPUT,12));
        topic.setMinimumHeight(dp(48));full(root,topic,10);

        tried=field(root,"What did you try?","Example: Imported a 30-second video and trimmed it.",2);
        outcome=field(root,"What happened? What should improve?","Explain the result and what you expected.",3);
        steps=field(root,"Steps to reproduce or an actionable suggestion","Example: Import video → Clips → Split, then export.",2);

        link=field(root,"Share a video or screenshot link (optional)",
            "https://drive.google.com/... or another shareable link",1);
        link.setSingleLine(true);
        link.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);

        TextView sharing=label("Only add media you agree to share. For large videos, paste a cloud link with view permission instead. The app does not upload files automatically.",12,false);
        sharing.setTextColor(0xffb9c5d9);full(root,sharing,5);

        full(root,button("Attach photos or videos",INPUT,this::pickMedia),4);
        mediaSummary=label("",12,false);mediaSummary.setTextColor(TEAL);root.addView(mediaSummary);
        mediaList=column();full(root,mediaList,12);

        TextView mailNotice=label("Submitting opens your email app. Review the message and attachments, then send it yourself. Do not include passwords or sensitive personal information.",12,false);
        mailNotice.setTextColor(0xffbac5d9);full(root,mailNotice,10);
        full(root,button("Continue to email",ACCENT,this::emailFeedback),12);

        if(state!=null){
            topic.setSelection(state.getInt("topic",0));
            tried.setText(state.getString("tried",""));
            outcome.setText(state.getString("outcome",""));
            steps.setText(state.getString("steps",""));
            link.setText(state.getString("link",""));
            ArrayList<String> selected=state.getStringArrayList("media");
            if(selected!=null)for(String uri:selected)addEvidence(Uri.parse(uri),false);
        }
        updateMedia();
        root.requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("topic",topic.getSelectedItemPosition());
        state.putString("tried",tried.getText().toString());
        state.putString("outcome",outcome.getText().toString());
        state.putString("steps",steps.getText().toString());
        state.putString("link",link.getText().toString());
        state.putStringArrayList("media",new ArrayList<>(evidence.keySet()));
        super.onSaveInstanceState(state);
    }

    private void pickMedia(){
        Intent select=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        select.addCategory(Intent.CATEGORY_OPENABLE);
        select.setType("*/*");
        select.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        select.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(select,PICK_MEDIA);}
        catch(ActivityNotFoundException e){toast("Unable to open photo/video picker.");}
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_MEDIA||result!=RESULT_OK||data==null)return;
        ClipData clips=data.getClipData();
        if(clips!=null){
            for(int i=0;i<clips.getItemCount();i++)addEvidence(clips.getItemAt(i).getUri(),true);
        }else if(data.getData()!=null)addEvidence(data.getData(),true);
        updateMedia();
    }

    private Evidence mediaInfo(Uri uri){
        if(!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()))return null;
        String type=getContentResolver().getType(uri);
        if(!FeedbackEvidencePolicy.isImageOrVideo(type))return null;
        String name=uri.getLastPathSegment();
        long size=-1;
        try(Cursor c=getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int display=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int length=c.getColumnIndex(OpenableColumns.SIZE);
                if(display>=0&&!c.isNull(display))name=c.getString(display);
                if(length>=0&&!c.isNull(length))size=c.getLong(length);
            }
        }catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name="Selected media";
        return new Evidence(uri,name,type,size);
    }

    private void addEvidence(Uri uri,boolean showErrors){
        if(uri==null||evidence.containsKey(uri.toString()))return;
        Evidence info;
        try{info=mediaInfo(uri);}catch(Exception e){info=null;}
        if(info==null){
            if(showErrors)toast("Please select a photo or video.");return;
        }
        long total=0;for(Evidence item:evidence.values())total+=item.size;
        if(!FeedbackEvidencePolicy.canAttach(evidence.size(),total,info.size)){
            if(showErrors)toast("Up to four photos/videos (15 MB total). For large videos, share a link.");
            return;
        }
        try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException ignored){
            // Still usable with the temporary grant received from the picker.
        }
        evidence.put(uri.toString(),info);
    }

    private void updateMedia(){
        mediaList.removeAllViews();
        mediaSummary.setText(tr("Selected evidence: ")+evidence.size()
            +tr(" of 4 files (15 MB maximum total)"));
        for(Evidence info:new ArrayList<>(evidence.values())){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=label(info.label+" · "+String.format(Locale.US,"%.1f MB",info.size/1048576.0),12,false);
            name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,-2,1);
            nameLp.setMargins(dp(6),0,dp(6),0);row.addView(name,nameLp);
            Button remove=button("Remove",INPUT,()->removeEvidence(info.uri));
            remove.setTextSize(12);
            row.addView(remove,new LinearLayout.LayoutParams(dp(86),dp(48)));
            full(mediaList,row,2);
        }
    }

    private void removeEvidence(Uri uri){
        if(evidence.remove(uri.toString())!=null){
            try{getContentResolver().releasePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            updateMedia();
        }
    }

    private void emailFeedback(){
        String a=tried.getText().toString().trim();
        String b=outcome.getText().toString().trim();
        String c=steps.getText().toString().trim();
        if(a.length()<6||b.length()<15||c.length()<10){
            toast("Please explain what you tried, the outcome and exact steps or a useful suggestion.");
            return;
        }
        String evidenceLink;
        try{evidenceLink=FeedbackEvidencePolicy.normalizeLink(link.getText().toString());}
        catch(IllegalArgumentException e){toast("Please enter an http(s) shareable link, or leave the link empty.");return;}

        String[] keys={"Teleprompter","Video editor","Captions","Export","Usability","Other"};
        String feature=keys[topic.getSelectedItemPosition()];
        StringBuilder text=new StringBuilder();
        text.append("Prompt Overlay feedback\nFeature: ").append(feature)
            .append("\nVersion: ").append(BuildConfig.VERSION_NAME)
            .append("\n\nWhat I tried:\n").append(a)
            .append("\n\nWhat happened / what should improve:\n").append(b)
            .append("\n\nSteps or concrete suggestion:\n").append(c);
        if(!evidenceLink.isEmpty())text.append("\n\nShared evidence link:\n").append(evidenceLink);
        if(!evidence.isEmpty()){
            text.append("\n\nAttached evidence (user-selected):\n");
            for(Evidence item:evidence.values())text.append("- ").append(item.label).append("\n");
        }
        text.append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append(" (Android ").append(Build.VERSION.RELEASE).append(")")
            .append("\n\nPrivate product feedback for our Pro Lifetime selection.")
            .append("\nNo public Google Play rating or review is required.");
        String subject="[Prompt Overlay feedback] "+feature+" · "+BuildConfig.VERSION_NAME;

        Intent send;
        if(evidence.isEmpty()){
            send=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:support@barndai.com"));
        }else{
            send=new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setType("message/rfc822");
            send.putExtra(Intent.EXTRA_EMAIL,new String[]{"support@barndai.com"});
            ArrayList<Uri> files=new ArrayList<>();
            for(Evidence item:evidence.values())files.add(item.uri);
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,files);
            ClipData clipData=ClipData.newUri(getContentResolver(),"Feedback evidence",files.get(0));
            for(int i=1;i<files.size();i++)clipData.addItem(new ClipData.Item(files.get(i)));
            send.setClipData(clipData);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        send.putExtra(Intent.EXTRA_SUBJECT,subject);
        send.putExtra(Intent.EXTRA_TEXT,text.toString());
        try{startActivity(Intent.createChooser(send,tr("Continue to email")));}
        catch(ActivityNotFoundException e){toast("No compatible email app. Email support@barndai.com or use a share link.");}
    }

    private void toast(String message){Toast.makeText(this,tr(message),Toast.LENGTH_LONG).show();}
}
