package com.costavong.promptoverlay.pro;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.costavong.promptoverlay.AppLanguage;
import com.costavong.promptoverlay.BuildConfig;
import com.costavong.promptoverlay.MainActivity;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Compact project library and four-track manual editor. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class ProActivity extends Activity {
    private final int BG=0xff131720,CARD=0xff202634,INPUT=0xff2b3344,ACCENT=0xff4b67ed,TEAL=0xff8bd4c5;
    private static final int PICK=501,SAVE_VIDEO=502;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout body;private ImageView preview;private TextView clock,status;private SeekBar seek;private ProgressBar progress;private Tracks tracks;
    private ProProject project;private String selected="",pending="",exportPath="",handled="";private long playheadUs=0;private int stillGeneration=0;
    private boolean localBusy=false,jobStarting=false;private Bitmap still;private Dialog playback;private ExoPlayer player;
    private PlayerView clipPlayerView;private Button clipPlay;private ExoPlayer clipPlayer;private ProClipPlayback clipPlayback;
    private final Runnable clipTick=()->updateClipPlayback();
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){jobUpdate();}};
    private String tr(String s){return ProStrings.t(this,s);}private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(true);
        if(saved!=null){pending=saved.getString("pending","");selected=saved.getString("selected","");playheadUs=saved.getLong("playhead");exportPath=saved.getString("export","");handled=saved.getString("handled","");load(saved.getString("project",""));}
        else load(getIntent().getStringExtra("project"));screen();consumeShare(getIntent());
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},90);
    }
    @Override protected void onStart(){super.onStart();androidx.core.content.ContextCompat.registerReceiver(this,receiver,new IntentFilter(ProExportService.UPDATE),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);jobUpdate();requestStill();}
    @Override protected void onStop(){syncClipClock();stopClipPlayback();if(player!=null)player.pause();unregisterReceiver(receiver);super.onStop();}
    @Override protected void onDestroy(){stopClipPlayback();stillGeneration++;worker.shutdownNow();if(player!=null)player.release();if(still!=null)still.recycle();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("project",project==null?"":project.id);b.putString("selected",selected);b.putString("pending",pending);b.putLong("playhead",playheadUs);b.putString("export",exportPath);b.putString("handled",handled);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("project")){load(intent.getStringExtra("project"));screen();}consumeShare(intent);}
    @Override public void onBackPressed(){if(project!=null&&!isBusy()){project=null;selected="";screen();}else if(!isBusy())super.onBackPressed();else toast("Wait for the current job or cancel it first.");}
    private boolean isBusy(){return localBusy||jobStarting||ProExportService.busy;}
    private void load(String id){if(id==null||!id.matches("[a-fA-F0-9-]{36}"))return;try{project=ProProject.load(new File(getFilesDir(),"pro/projects/"+id));if(!project.clips.isEmpty()&&project.clip(selected)==null)selected=project.clips.get(0).id;}catch(Exception e){error(e);}}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(tr(s));t.setTextColor(0xfff3f5fb);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(6),0,dp(6));return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(tr(label));b.setTextSize(13);b.setTextColor(0xfff6f8ff);b.setAllCaps(false);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setBackground(shape(INPUT,14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(dp(3),dp(3),dp(3),dp(3));b.setLayoutParams(lp);b.setOnClickListener(v->{if(!isBusy())action.run();else toast("Wait for the current job or cancel it first.");});return b;}
    private void pairControls(LinearLayout parent,View...controls){
        LinearLayout r=row();for(View control:controls){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(3),dp(0),dp(3),dp(0));r.addView(control,lp);}parent.addView(r);
    }
    private void pair(LinearLayout parent,Button...buttons){LinearLayout r=row();for(Button b:buttons){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(b,lp);}parent.addView(r);}
    private void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void screen(){stopClipPlayback();stillGeneration++;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);body=column();body.setPadding(dp(18),dp(12),dp(18),dp(32));body.setLayoutDirection(AppLanguage.layoutDirection(this));scroll.addView(body);setContentView(scroll);
        if(Build.VERSION.SDK_INT>=35){scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});scroll.requestApplyInsets();}
        body.addView(text("Prompt Overlay",project==null?28:22,true));if(project==null)body.addView(text("Pro test · purchases are disabled",13,false));
        if(project!=null)body.addView(button("Back",()->{project=null;screen();}));
        status=text("",14,false);status.setTextColor(TEAL);body.addView(status);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);body.addView(progress);progress.setVisibility(View.GONE);
        if(project==null)library();else editor();jobStatusOnly();}
    private void language(){pauseClipPlayback();new AlertDialog.Builder(this).setTitle(tr("Language")).setSingleChoiceItems(AppLanguage.languageLabels(),AppLanguage.indexOf(this),(d,n)->{AppLanguage.set(this,AppLanguage.codeAt(n));d.dismiss();screen();}).setNegativeButton(tr("Cancel"),null).show();}
    private void library(){gap(body,8);
        body.addView(text("Choose what you want to do",22,true));
        body.addView(text("Use the teleprompter while recording, or edit a video from your camera.",15,false));
        Button teleprompter=button("Open teleprompter",()->startActivity(new Intent(this,MainActivity.class).putExtra("open_basic",true)));
        teleprompter.setBackground(shape(ACCENT,14));body.addView(teleprompter);
        body.addView(button("Edit a video",()->newProject(false)));
        gap(body,10);body.addView(text("Your projects",19,true));
        List<ProProject> projects=ProProject.all(this);
        if(projects.isEmpty())body.addView(text("No projects yet. Tap Edit a video to start, then tap Import video.",16,false));
        for(ProProject p:projects){LinearLayout card=column();card.setPadding(dp(14),dp(10),dp(14),dp(12));card.setBackground(shape(CARD,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(10),0,0);body.addView(card,lp);
            card.addView(projectName(p.name));card.addView(text(p.clips.size()+" · "+seconds(p.timeline().durationUs)+"s · "+p.format,14,false));
            pair(card,button("Open",()->{project=p;selected=p.clips.isEmpty()?"":p.clips.get(0).id;playheadUs=0;screen();}),button("Delete",()->new AlertDialog.Builder(this).setMessage(tr("Delete this project and its imported files?"))
                .setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Delete"),(d,w)->{ProRenderEngine.erase(p.directory(this));screen();}).show()));}
        gap(body,14);pair(body,button("Language",this::language),button("Rate app",this::openPlayListing));
        body.addView(button("Privacy",()->ProPrivacy.show(this)));body.addView(text("v"+BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE,12,false));}
    private TextView projectName(String value){TextView name=text(value,18,true);name.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return name;}
    private void newProject(boolean pick){project=new ProProject();selected="";playheadUs=0;try{project.save(this);}catch(Exception e){error(e);}screen();if(pick)pick("video","video/*");}
    private void editor(){pairControls(body,
            button("Rename",()->{pauseClipPlayback();EditText name=field(null,"Project name",project.name,false);new AlertDialog.Builder(this).setTitle(tr("Rename")).setView(name).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Save"),(d,w)->{project.name=name.getText().toString().trim();if(project.name.isEmpty())project.name="Untitled video";changed(true);}).show();}),
            button("Import video",()->pick("video","video/*")));
        body.addView(projectName(project.name));FrameLayout display=new FrameLayout(this);display.setBackground(shape(0xff080b10,16));body.addView(display,new LinearLayout.LayoutParams(-1,dp(170)));
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);display.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        clipPlayerView=new PlayerView(this);clipPlayerView.setUseController(false);clipPlayerView.setVisibility(View.GONE);display.addView(clipPlayerView,new FrameLayout.LayoutParams(-1,-1));if(project.clips.isEmpty()){TextView empty=text("Tap Import video to begin",14,false);empty.setGravity(Gravity.CENTER);display.addView(empty,new FrameLayout.LayoutParams(-1,-1));}
        clipPlay=button("Play clip",this::toggleClipPlayback);clipPlay.setBackground(shape(ACCENT,14));body.addView(clipPlay,new LinearLayout.LayoutParams(-1,dp(50)));
        clock=text("",14,false);clock.setGravity(Gravity.CENTER);body.addView(clock);seek=new SeekBar(this);seek.setMax(10000);seek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&!isBusy()){playheadUs=Math.round(project.timeline().durationUs*value/10000.0);clock();}}public void onStartTrackingTouch(SeekBar s){syncClipClock();stopClipPlayback();}public void onStopTrackingTouch(SeekBar s){List<ProTimeline.Entry> active=project.timeline().at(playheadUs);if(!active.isEmpty()&&!active.get(active.size()-1).id.equals(selected)){selected=active.get(active.size()-1).id;screen();}else requestStill();}});body.addView(seek);
        tracks=new Tracks();body.addView(tracks,new LinearLayout.LayoutParams(-1,dp(128)));
        HorizontalScrollView clips=new HorizontalScrollView(this);LinearLayout clipRow=row();clips.addView(clipRow);for(int i=0;i<project.clips.size();i++){ProProject.Clip c=project.clips.get(i);Button b=button((i+1)+" · "+c.name,()->{selected=c.id;playheadUs=project.timeline().byId(c.id).startUs;screen();});b.setBackground(shape(c.id.equals(selected)?ACCENT:INPUT,12));clipRow.addView(b,new LinearLayout.LayoutParams(dp(160),dp(44)));}body.addView(clips);
        pairControls(body,
            button("Clips",this::clipActions),
            button("Captions",this::captionActions),
            button("Graphics",this::graphicActions));
        pairControls(body,button("Music",this::musicActions),button("Format",this::format));
        gap(body,8);Button render=button("Edited preview",()->job("preview",720,"",""));Button export=button("Export",()->{pauseClipPlayback();new AlertDialog.Builder(this).setTitle(tr("Export")).setItems(new String[]{"720p · MP4","1080p · MP4"},(d,n)->job("export",n==0?720:1080,"","")).show();});export.setBackground(shape(ACCENT,14));pairControls(body,render,export);
        body.addView(text("Play clip starts immediately. Edited preview includes captions, graphics and music.",13,false));body.addView(text("Changes saved on this phone",13,false));clock();requestStill();}
    private void toggleClipPlayback(){ProProject.Clip c=selected();if(c==null)return;
        if(clipPlayer!=null){if(clipPlayer.getPlayWhenReady()&&clipPlayer.getPlaybackState()!=Player.STATE_ENDED)pauseClipPlayback();else{if(clipPlayer.getPlaybackState()==Player.STATE_ENDED)clipPlayer.seekTo(0);clipPlayer.play();updateClipPlayback();}return;}
        ProTimeline.Entry entry=project.timeline().byId(c.id);if(entry==null)return;clipPlayback=new ProClipPlayback(entry);
        if(playheadUs<entry.startUs||playheadUs>=entry.endUs)playheadUs=entry.startUs;
        ExoPlayer started=new ExoPlayer.Builder(this).build();clipPlayer=started;clipPlayerView.setPlayer(started);clipPlayerView.setVisibility(View.VISIBLE);
        started.addListener(new Player.Listener(){
            @Override public void onIsPlayingChanged(boolean playing){if(clipPlayer==started)updateClipPlayback();}
            @Override public void onPlaybackStateChanged(int state){if(clipPlayer==started)updateClipPlayback();}
            @Override public void onPlayerError(PlaybackException e){if(clipPlayer==started){stopClipPlayback();requestStill();error(e);}}
        });
        long positionMs=clipPlayback.positionMs(playheadUs);
        started.setMediaItem(new MediaItem.Builder().setUri(Uri.fromFile(new File(c.path))).setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clipPlayback.startMs).setEndPositionMs(clipPlayback.endMs).build()).build(),positionMs);
        started.setPlaybackSpeed((float)c.speed);started.setVolume(c.mute?0:(float)Math.max(0,Math.min(1,c.volume)));started.prepare();started.play();updateClipPlayback();
    }
    private void syncClipClock(){if(clipPlayer!=null&&clipPlayback!=null&&project!=null){playheadUs=clipPlayback.timelineUs(clipPlayer.getCurrentPosition());clock();}}
    private void updateClipPlayback(){main.removeCallbacks(clipTick);if(clipPlayer==null)return;syncClipClock();boolean running=clipPlayer.getPlayWhenReady()&&clipPlayer.getPlaybackState()!=Player.STATE_ENDED;
        if(clipPlay!=null)clipPlay.setText(tr(running?"Pause":"Play clip"));if(running)main.postDelayed(clipTick,100);}
    private void pauseClipPlayback(){if(clipPlayer!=null){clipPlayer.pause();updateClipPlayback();}}
    private void stopClipPlayback(){main.removeCallbacks(clipTick);ExoPlayer old=clipPlayer;clipPlayer=null;clipPlayback=null;if(clipPlayerView!=null){clipPlayerView.setPlayer(null);clipPlayerView.setVisibility(View.GONE);}if(old!=null)old.release();if(clipPlay!=null)clipPlay.setText(tr("Play clip"));}
    private void clock(){if(clock==null||project==null)return;long duration=project.timeline().durationUs;playheadUs=Math.max(0,Math.min(Math.max(0,duration-1),playheadUs));clock.setText(seconds(playheadUs)+" / "+seconds(duration)+" s");if(seek!=null)seek.setProgress(duration==0?0:(int)(playheadUs*10000/duration));if(tracks!=null)tracks.invalidate();}
    private static String seconds(long us){return String.format(Locale.US,"%.2f",us/1_000_000.0);}
    private void requestStill(){if(project==null||project.clips.isEmpty()||isBusy())return;int token=++stillGeneration;try{ProProject snapshot=ProProject.from(project.json());long time=playheadUs;
            worker.execute(()->{Bitmap b=null;try{if(token!=stillGeneration)return;int[] size=snapshot.outputSize(360);try(ProFrameRenderer r=new ProFrameRenderer(snapshot,size[0],size[1])){b=r.render(time).copy(Bitmap.Config.ARGB_8888,false);}Bitmap result=b;main.post(()->{if(token!=stillGeneration||isDestroyed()){result.recycle();return;}Bitmap old=still;still=result;preview.setImageBitmap(result);if(old!=null)old.recycle();});}catch(Exception e){if(b!=null)b.recycle();main.post(()->{if(token==stillGeneration)status.setText(e.getMessage());});}});
        }catch(Exception e){error(e);}}
    private void changed(boolean rebuild){try{project.revision++;project.save(this);exportPath="";if(rebuild)screen();else{clock();requestStill();}}catch(Exception e){error(e);}}
    private ProProject.Clip selected(){ProProject.Clip c=project==null?null:project.clip(selected);if(c==null)toast("Select a clip first.");return c;}
    private void choices(String title,String[] options,java.util.function.IntConsumer action){ScrollView scroll=new ScrollView(this);LinearLayout list=column();list.setPadding(dp(16),dp(8),dp(16),dp(8));list.setLayoutDirection(AppLanguage.layoutDirection(this));scroll.addView(list);
        pauseClipPlayback();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(tr(title)).setView(scroll).setNegativeButton(tr("Cancel"),null).create();
        for(int n=0;n<options.length;n++){final int index=n;Button option=button(options[n],()->{dialog.dismiss();action.accept(index);});option.setMinHeight(dp(54));option.setMaxLines(3);option.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));list.addView(option,lp);}
        dialog.show();dialog.getWindow().setBackgroundDrawable(shape(CARD,20));}
    private void clipActions(){if(selected()==null)return;choices("Clips",new String[]{"Edit selected clip","Split at playhead","Move earlier","Move later","Delete clip"},n->{try{ProProject.Clip c=selected();if(c==null)return;int index=project.clips.indexOf(c);
            if(n==0){editClip(c);return;}if(n==1)project.split(c.id,playheadUs);if(n==2&&index>0)Collections.swap(project.clips,index,index-1);if(n==3&&index+1<project.clips.size())Collections.swap(project.clips,index,index+1);if(n==4){project.clips.remove(c);project.layers.removeIf(l->l.clipId.equals(c.id));selected=project.clips.isEmpty()?"":project.clips.get(0).id;}changed(true);
        }catch(Exception e){error(e);}});}
    private interface SaveForm {void save()throws Exception;}
    private void form(String title,LinearLayout content,SaveForm save){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);content.setPadding(dp(20),dp(4),dp(20),dp(20));content.setLayoutDirection(AppLanguage.layoutDirection(this));content.setBackgroundColor(CARD);
        pauseClipPlayback();
        AlertDialog d=new AlertDialog.Builder(this).setTitle(tr(title)).setView(scroll).setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Save"),null).create();d.setOnShowListener(v->{d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{save.save();d.dismiss();changed(true);}catch(Exception e){error(e);}});});d.show();}
    private EditText field(LinearLayout parent,String label,String value,boolean numeric){EditText e=new EditText(this);e.setTextColor(0xfff5f7fd);e.setHintTextColor(0xffa1adc4);e.setTextSize(16);e.setText(value);e.setBackground(shape(INPUT,12));e.setPadding(dp(12),dp(10),dp(12),dp(10));
        e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if(numeric)e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);if(parent!=null){parent.addView(text(label,14,false));parent.addView(e,new LinearLayout.LayoutParams(-1,-2));}return e;}
    private EditText number(LinearLayout parent,String label,double value){return field(parent,label,String.format(Locale.US,"%.3f",value).replaceAll("0+$","").replaceAll("\\.$",""),true);}
    private double value(EditText e,double min,double max)throws IOException{try{String raw=e.getText().toString().trim();StringBuilder normalized=new StringBuilder();for(char c:raw.toCharArray()){int digit=Character.digit(c,10);normalized.append(digit>=0?(char)('0'+digit):(c==','||c=='\u066b'?'.':c));}double v=Double.parseDouble(normalized.toString());if(!Double.isFinite(v)||v<min||v>max)throw new NumberFormatException();return v;}catch(Exception x){throw new IOException("Enter a number between "+min+" and "+max+".");}}
    private CheckBox check(LinearLayout parent,String label,boolean state){CheckBox c=new CheckBox(this);c.setText(tr(label));c.setTextColor(0xfff3f5fb);c.setChecked(state);parent.addView(c);return c;}
    private Spinner spinner(LinearLayout p,String label,String[] options,int selected){p.addView(text(label,14,false));Spinner s=new Spinner(this);String[] localized=Arrays.stream(options).map(this::tr).toArray(String[]::new);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,localized));s.setSelection(selected);p.addView(s);return s;}
    private void editClip(ProProject.Clip c){LinearLayout f=column();EditText in=number(f,"Start (seconds)",c.inUs/1e6),out=number(f,"End (seconds)",c.outUs/1e6);double[] speeds={.5,1,1.5,2};int idx=1;for(int i=0;i<4;i++)if(speeds[i]==c.speed)idx=i;Spinner speed=spinner(f,"Speed",new String[]{"0.5×","1×","1.5×","2×"},idx);
        EditText fade=number(f,"Crossfade into this clip (seconds)",c.crossfadeUs/1e6),volume=number(f,"Original sound volume (%)",c.volume*100);CheckBox mute=check(f,"Mute original sound",c.mute);
        EditText left=number(f,"Crop left (%)",c.cropLeft*100),top=number(f,"Crop top (%)",c.cropTop*100),right=number(f,"Crop right (%)",c.cropRight*100),bottom=number(f,"Crop bottom (%)",c.cropBottom*100);
        EditText bright=number(f,"Brightness (-100 to 100)",c.brightness*100),contrast=number(f,"Contrast (%)",c.contrast*100),sat=number(f,"Saturation (%)",c.saturation*100),warm=number(f,"Temperature (-100 to 100)",c.temperature*100);
        CheckBox green=check(f,"Green screen (experimental)",c.green);EditText threshold=number(f,"Green threshold (%)",c.greenThreshold*100),feather=number(f,"Edge softness (%)",c.greenFeather*100);
        form("Edit selected clip",f,()->{long a=Math.round(value(in,0,c.sourceDurationUs/1e6)*1e6),b=Math.round(value(out,0,c.sourceDurationUs/1e6)*1e6);if(b-a<100000)throw new IOException("Keep at least 0.1 seconds of video.");
            float l=(float)value(left,0,99)/100,t=(float)value(top,0,99)/100,r=(float)value(right,1,100)/100,bt=(float)value(bottom,1,100)/100;if(r-l<.02||bt-t<.02)throw new IOException("The crop area is too small.");
            double v=value(volume,0,200)/100,br=value(bright,-100,100)/100,co=value(contrast,0,200)/100,sa=value(sat,0,200)/100,wa=value(warm,-100,100)/100,fa=value(fade,0,10);
            float th=(float)value(threshold,0,100)/100,fe=(float)value(feather,1,100)/100;
            c.inUs=a;c.outUs=b;c.speed=speeds[speed.getSelectedItemPosition()];c.crossfadeUs=Math.round(fa*1e6);c.volume=v;c.mute=mute.isChecked();c.cropLeft=l;c.cropTop=t;c.cropRight=r;c.cropBottom=bt;c.brightness=br;c.contrast=co;c.saturation=sa;c.temperature=wa;c.green=green.isChecked();c.greenThreshold=th;c.greenFeather=fe;});}
    private void captionActions(){choices("Captions",new String[]{"Automatic captions","Add caption","Edit captions"},n->{if(n==0){if(selected()==null)return;choices("Automatic captions",new String[]{"English","Español","Français"},language->{String[] codes={"en","es","fr"};new AlertDialog.Builder(this).setMessage(tr("Download a speech pack (about 40 MB) if needed, then caption this clip on your phone. This replaces this clip’s captions. Arabic: use Add caption."))
                    .setNegativeButton(tr("Cancel"),null).setPositiveButton(tr("Automatic captions"),(d,w)->job("captions",0,selected,codes[language])).show();});}
            else if(n==1){if(selected()!=null)editLayer(newLayer("caption"),true);}else layers("caption");});}
    private ProProject.Layer newLayer(String kind){ProProject.Layer l=new ProProject.Layer();l.kind=kind;ProTimeline.Entry e=project.timeline().byId(selected);
        if(e!=null&&!"image".equals(kind)){l.clipId=e.id;l.startUs=e.sourceAt(playheadUs);l.endUs=Math.min(e.sourceEndUs,l.startUs+3_000_000);}else{l.startUs=playheadUs;l.endUs=Math.min(project.timeline().durationUs,l.startUs+3_000_000);}
        if("image".equals(kind)){l.wholeVideo=true;l.startUs=0;l.endUs=project.timeline().durationUs;l.x=.85f;l.y=.12f;l.size=.18f;l.background=0;}return l;}
    private void graphicActions(){choices("Graphics",new String[]{"Add title","Import logo or image","Use last logo","Edit graphics","Green-screen background"},n->{if(n==0)editLayer(newLayer("text"),true);if(n==1)pick("logo","image/*");if(n==2){File last=new File(getFilesDir(),"pro/last-logo");if(last.exists()){ProProject.Layer l=newLayer("image");l.path=last.getPath();editLayer(l,true);}else toast("Import a logo first.");}if(n==3)layers("graphics");if(n==4)pick("green","image/*");});}
    private void layers(String kind){List<ProProject.Layer> list=new ArrayList<>();for(ProProject.Layer l:project.layers)if("caption".equals(kind)=="caption".equals(l.kind))list.add(l);
        if(list.isEmpty()){toast("No items yet.");return;}String[] labels=list.stream().map(l->("image".equals(l.kind)?new File(l.path).getName():l.text)+" · "+seconds(l.start(project.timeline()))+"s").toArray(String[]::new);
        choices("caption".equals(kind)?"Edit captions":"Edit graphics",labels,n->{ProProject.Layer l=list.get(n);choices("Text",new String[]{"Open","Delete"},action->{if(action==0)editLayer(l,false);else{project.layers.remove(l);changed(true);}});});}
    private void editLayer(ProProject.Layer original,boolean fresh){try{ProProject.Layer l=ProProject.Layer.from(original.json());LinearLayout f=column();boolean image="image".equals(l.kind);EditText content=image?null:field(f,"Text",l.text,false);
        TextView timing=text(l.clipId.isEmpty()?"Timeline times":"Source times in selected clip; timing follows speed and reordering.",13,false);f.addView(timing);
        String anchorId=l.clipId.isEmpty()?selected:l.clipId;ProProject.Clip anchor=project.clip(anchorId);ProTimeline.Entry anchorEntry=project.timeline().byId(anchorId);
        CheckBox attach=!"caption".equals(l.kind)&&anchor!=null?check(f,"Attach to selected clip",!l.clipId.isEmpty()):null;
        CheckBox whole=image?check(f,"Show throughout video",l.wholeVideo):null;
        EditText start=number(f,"Start (seconds)",l.wholeVideo?0:l.startUs/1e6),end=number(f,"End (seconds)",l.wholeVideo?project.timeline().durationUs/1e6:l.endUs/1e6),x=number(f,"Horizontal position (%)",l.x*100),y=number(f,"Vertical position (%)",l.y*100),size=number(f,"Size (% of width)",l.size*100),opacity=number(f,"Opacity (%)",l.opacity*100);
        if(whole!=null){start.setEnabled(!whole.isChecked());end.setEnabled(!whole.isChecked());whole.setOnCheckedChangeListener((v,on)->{l.wholeVideo=on;start.setEnabled(!on);end.setEnabled(!on);if(on){if(attach!=null)attach.setChecked(false);l.clipId="";start.setText("0");end.setText(seconds(project.timeline().durationUs));}});if(attach!=null)attach.setOnClickListener(v->{if(attach.isChecked())whole.setChecked(false);});}
        if(attach!=null)attach.setOnCheckedChangeListener((v,on)->{try{long a=Math.round(Double.parseDouble(start.getText().toString())*1e6),b=Math.round(Double.parseDouble(end.getText().toString())*1e6);if(on){a=anchorEntry.sourceAt(a);b=anchorEntry.sourceAt(b)+1;l.clipId=anchorId;}else{a=anchorEntry.mapSource(a);b=anchorEntry.mapSource(b);l.clipId="";}start.setText(seconds(a));end.setText(seconds(b));timing.setText(tr(on?"Source times in selected clip; timing follows speed and reordering.":"Timeline times"));}catch(Exception e){error(e);}});
        int[] colours={0xffffffff,0xffffe24d,0xff4494ff,0xffff5a9b,0xff000000};String[] names={"White","Yellow","Blue","Pink","Black"};int colorIndex=0;for(int i=0;i<colours.length;i++)if(l.color==colours[i])colorIndex=i;
        Spinner color=image?null:spinner(f,"Colour",names,colorIndex),background=image?null:spinner(f,"Background",new String[]{"Black","Transparent"},l.background==0?1:0);CheckBox bold=image?null:check(f,"Bold",l.bold);
        form(image?"Graphics":"Text",f,()->{double limit=l.clipId.isEmpty()?project.timeline().durationUs/1e6:anchor.sourceDurationUs/1e6;long a=l.wholeVideo?0:Math.round(value(start,0,limit)*1e6),b=l.wholeVideo?project.timeline().durationUs:Math.round(value(end,0,limit)*1e6);if(b<=a)throw new IOException("End must come after start.");if(!image&&content.getText().toString().trim().isEmpty())throw new IOException("Enter some text.");
            l.x=(float)value(x,0,100)/100;l.y=(float)value(y,0,100)/100;l.size=(float)value(size,1,image?100:30)/100;l.opacity=(float)value(opacity,0,100)/100;l.startUs=a;l.endUs=b;if(!image){l.text=content.getText().toString();l.color=colours[color.getSelectedItemPosition()];l.background=background.getSelectedItemPosition()==1?0:0x99000000;l.bold=bold.isChecked();}
            if(!fresh)project.layers.remove(original);project.layers.add(l);});}catch(Exception e){error(e);}}
    private void musicActions(){choices("Music",new String[]{"Import music","Edit music","Remove music"},n->{if(n==0)pick("music","audio/*");if(n==1){if(project.music!=null)editMusic();else toast("Import music first.");}if(n==2){project.music=null;changed(true);}});}
    private void editMusic(){ProProject.Music m=project.music;LinearLayout f=column();f.addView(text(m.name,16,true));long sourceDuration=duration(m.path);
        EditText in=number(f,"Start (seconds)",m.inUs/1e6),out=number(f,"End (seconds)",m.outUs/1e6),start=number(f,"Timeline start (seconds)",m.startUs/1e6),volume=number(f,"Volume (%)",m.volume*100),fadeIn=number(f,"Fade in (seconds)",m.fadeInUs/1e6),fadeOut=number(f,"Fade out (seconds)",m.fadeOutUs/1e6);
        form("Edit music",f,()->{long a=Math.round(value(in,0,sourceDuration/1e6)*1e6),b=Math.round(value(out,0,sourceDuration/1e6)*1e6);if(b<=a)throw new IOException("End must come after start.");long st=Math.round(value(start,0,project.timeline().durationUs/1e6)*1e6),fi=Math.round(value(fadeIn,0,(b-a)/1e6)*1e6),fo=Math.round(value(fadeOut,0,(b-a)/1e6)*1e6);double v=value(volume,0,200)/100;m.inUs=a;m.outUs=b;m.startUs=st;m.fadeInUs=fi;m.fadeOutUs=fo;m.volume=v;});}
    private long duration(String path){MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(path);return Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))*1000;}catch(Exception e){error(e);return 0;}finally{try{m.release();}catch(Exception ignored){}}}
    private void format(){String[] formats={"Original","9:16","16:9","1:1","4:5"};choices("Format",formats,n->{project.format=formats[n];changed(true);});}
    private void pick(String kind,String type){pending=kind;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE);if("video".equals(kind))intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(intent,PICK);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==PICK){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());importFiles(uris,pending);}
        if(request==SAVE_VIDEO&&data.getData()!=null){Uri uri=data.getData();String path=exportPath;localBusy=true;status.setText(tr("Save video"));worker.execute(()->{try(InputStream in=new FileInputStream(path);OutputStream out=getContentResolver().openOutputStream(uri)){copy(in,out);main.post(()->{toast("Saved.");main.postDelayed(this::maybePromptForRating,1800);});}catch(Exception e){main.post(()->error(e));}finally{main.post(()->{localBusy=false;if(!isDestroyed())jobStatusOnly();});}});}}
    private void consumeShare(Intent intent){String action=intent.getAction();if(!Intent.ACTION_SEND.equals(action)&&!Intent.ACTION_SEND_MULTIPLE.equals(action))return;if(isBusy()){toast("Wait for the current job or cancel it first.");return;}
        ArrayList<Uri> uris=new ArrayList<>();if(Intent.ACTION_SEND.equals(action)){Uri u=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null)uris.add(u);}else{ArrayList<Uri> list=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(list!=null)uris.addAll(list);}intent.setAction(null);if(uris.isEmpty())return;newProject(false);importFiles(uris,"video");}
    private String displayName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Imported file";}
    private void importFiles(List<Uri> uris,String kind){if(uris.isEmpty())return;if(project==null)newProject(false);localBusy=true;status.setText(tr("Import"));ProProject target=project;
        worker.execute(()->{String problem=null;ProProject.Layer logo=null;try{for(Uri uri:uris){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled.");String name=displayName(uri);File destination=new File(target.directory(this),UUID.randomUUID()+"-media");
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(destination)){copy(in,out);}catch(Exception e){destination.delete();throw e;}
                try{if("video".equals(kind)){ProProject.Clip c=ProProject.inspect(destination);c.name=name;if(target.clips.isEmpty()){target.format="Original";if("Untitled video".equals(target.name))target.name=name.replaceFirst("\\.[^.]+$","");}target.clips.add(c);selected=c.id;}
                    else if("music".equals(kind)){long d=duration(destination.getPath());if(d<=0)throw new IOException("Cannot read this audio file.");ProProject.Music m=new ProProject.Music();m.path=destination.getPath();m.name=name;m.outUs=d;target.music=m;}
                    else{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(destination.getPath(),bounds);if(bounds.outWidth<1)throw new IOException("Cannot read this image.");
                        if("green".equals(kind))target.greenBackground=destination.getPath();else{File last=new File(getFilesDir(),"pro/last-logo");try(InputStream in=new FileInputStream(destination);OutputStream out=new FileOutputStream(last)){copy(in,out);}logo=newLayer("image");logo.path=destination.getPath();}}
                }catch(Exception e){destination.delete();throw e;}}
            if("video".equals(kind)&&target.timeline().byId(selected)!=null)playheadUs=target.timeline().byId(selected).startUs;target.revision++;target.save(this);
        }catch(Exception e){problem=e.getMessage();try{target.save(this);}catch(Exception ignored){}}String failure=problem;ProProject.Layer layer=logo;main.post(()->{localBusy=false;if(isDestroyed())return;screen();if(failure!=null)toast(failure);if(layer!=null)editLayer(layer,true);});});}
    private static void copy(InputStream in,OutputStream out)throws IOException{if(in==null||out==null)throw new IOException("Cannot open this file.");byte[] b=new byte[65536];int n;while((n=in.read(b))>=0){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled.");out.write(b,0,n);}}
    private void job(String kind,int resolution,String clip,String language){if(project.clips.isEmpty()){toast("Import a video first.");return;}syncClipClock();stopClipPlayback();try{project.save(this);handled="";
        File jobs=new File(getFilesDir(),"pro/jobs");jobs.mkdirs();File snapshot=new File(jobs,UUID.randomUUID()+".json");try(OutputStream out=new FileOutputStream(snapshot)){out.write(project.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        Intent i=new Intent(this,ProExportService.class).putExtra("project",project.id).putExtra("snapshot_file",snapshot.getPath()).putExtra("kind",kind).putExtra("resolution",resolution).putExtra("clip",clip).putExtra("language",language);jobStarting=true;startForegroundService(i);status.setText(tr("Starting…"));progress.setVisibility(View.VISIBLE);main.postDelayed(this::jobUpdate,500);
        }catch(Exception e){jobStarting=false;error(e);}}
    private void jobStatusOnly(){if(status==null)return;if(ProExportService.busy){status.setText(tr(ProExportService.stage)+" · "+ProExportService.percent+"%");progress.setProgress(ProExportService.percent);progress.setVisibility(View.VISIBLE);
            status.setOnClickListener(v->new AlertDialog.Builder(this).setMessage(tr("Cancel")+"?").setNegativeButton(tr("Back"),null).setPositiveButton(tr("Cancel"),(d,w)->startService(new Intent(this,ProExportService.class).setAction("cancel"))).show());}else if(!localBusy){progress.setVisibility(View.GONE);status.setOnClickListener(null);status.setText("");}}
    private void jobUpdate(){if(ProExportService.busy||project!=null&&project.id.equals(ProExportService.projectId))jobStarting=false;jobStatusOnly();if(ProExportService.busy||project==null||!project.id.equals(ProExportService.projectId))return;String result=ProExportService.kind+ProExportService.output+ProExportService.error+ProExportService.percent;
        if(result.equals(handled)||result.isEmpty())return;handled=result;if(!ProExportService.error.isEmpty()){toast(ProExportService.error);return;}
        if("captions".equals(ProExportService.kind)){load(project.id);screen();}else if(!ProExportService.output.isEmpty()){exportPath=ProExportService.output;if("preview".equals(ProExportService.kind))play(exportPath);else outputOptions();}}
    private void play(String path){pauseClipPlayback();if(player!=null){player.release();player=null;}Dialog d=new Dialog(this,android.R.style.Theme_Material_NoActionBar);LinearLayout layout=column();layout.setBackgroundColor(BG);PlayerView view=new PlayerView(this);layout.addView(view,new LinearLayout.LayoutParams(-1,0,1));layout.addView(button("Back",d::dismiss));d.setContentView(layout);player=new ExoPlayer.Builder(this).build();view.setPlayer(player);player.setMediaItem(MediaItem.fromUri(Uri.fromFile(new File(path))));player.prepare();player.play();d.setOnDismissListener(v->{if(player!=null){player.release();player=null;}});d.show();d.getWindow().setLayout(-1,-1);playback=d;}
    private void outputOptions(){choices("Finished video",new String[]{"Preview","Save video","Share"},n->{if(n==0)play(exportPath);if(n==1)startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("video/mp4").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,project.name.replaceAll("[\\\\/:*?\"<>|]","_")+".mp4"),SAVE_VIDEO);
            if(n==2){Uri u=FileProvider.getUriForFile(this,getPackageName()+".profiles",new File(exportPath));Intent share=new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("Video",u));startActivity(Intent.createChooser(share,tr("Share")));}});}
    private void error(Throwable e){toast(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    private void openPlayListing(){Uri listing=Uri.parse("market://details?id=com.costavong.promptoverlay");try{startActivity(new Intent(Intent.ACTION_VIEW,listing));}catch(ActivityNotFoundException e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.costavong.promptoverlay")));}}
    private void maybePromptForRating(){if(isFinishing()||isDestroyed())return;android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);if(prefs.getBoolean("rating_prompt_shown",false))return;
        prefs.edit().putBoolean("rating_prompt_shown",true).apply();
        new AlertDialog.Builder(this).setTitle(tr("Enjoying Prompt Overlay?"))
            .setMessage(tr("If Prompt Overlay has been useful, would you rate it on Google Play?"))
            .setPositiveButton(tr("Rate us"),(d,w)->openPlayListing()).setNegativeButton(tr("Not now"),null).show();}

    private void toast(String s){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->toast(s));return;}Toast.makeText(this,tr(s),Toast.LENGTH_LONG).show();}
    private final class Tracks extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Tracks(){super(ProActivity.this);setBackground(shape(CARD,14));setLayoutDirection(View.LAYOUT_DIRECTION_LTR);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(project==null)return;float label=dp(70),w=getWidth()-label-dp(8),h=getHeight()/4f;long duration=Math.max(1,project.timeline().durationUs);ProTimeline timeline=project.timeline();String[] names={"Video","Graphics","Captions","Sound"};
            for(int r=0;r<4;r++){p.setColor(0xff9eabc3);p.setTextSize(dp(11));c.drawText(tr(names[r]),dp(7),r*h+h*.6f,p);p.setColor(0xff313b50);c.drawLine(label,(r+1)*h-2,getWidth(),(r+1)*h-2,p);}
            for(ProTimeline.Entry e:timeline.entries){p.setColor(e.id.equals(selected)?ACCENT:0xff354b8b);block(c,e.startUs,e.endUs,0,label,w,h,duration);p.setColor(0xff49786e);if(!project.clip(e.id).mute)block(c,e.startUs,e.endUs,3,label,w,h,duration);}
            for(ProProject.Layer l:project.layers){p.setColor("caption".equals(l.kind)?0xff8b6dd4:0xffaa7750);block(c,l.start(timeline),l.end(timeline),"caption".equals(l.kind)?2:1,label,w,h,duration);}
            if(project.music!=null){p.setColor(TEAL);block(c,project.music.startUs,Math.min(duration,project.music.startUs+project.music.outUs-project.music.inUs),3,label,w,h,duration);}
            p.setColor(0xffffffff);p.setStrokeWidth(dp(2));float x=label+w*playheadUs/duration;c.drawLine(x,0,x,getHeight(),p);}
        private void block(Canvas c,long a,long b,int row,float label,float w,float h,long duration){float x=label+w*a/duration,right=label+w*b/duration;c.drawRoundRect(x,row*h+dp(8),Math.max(x+dp(2),right),(row+1)*h-dp(8),dp(5),dp(5),p);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_UP&&!isBusy()){float label=dp(70),w=getWidth()-label-dp(8);playheadUs=Math.max(0,Math.min(project.timeline().durationUs-1,(long)((e.getX()-label)/w*project.timeline().durationUs)));int row=(int)(e.getY()/(getHeight()/4f));
                stopClipPlayback();
                if(row==0){List<ProTimeline.Entry> active=project.timeline().at(playheadUs);if(!active.isEmpty())selected=active.get(active.size()-1).id;screen();}
                else if(row==1||row==2){for(ProProject.Layer l:project.layers)if((row==2)=="caption".equals(l.kind)&&l.visible(project.timeline(),playheadUs)){editLayer(l,false);break;}clock();requestStill();invalidate();}
                else{clock();requestStill();invalidate();}return true;}return true;}
    }
}
