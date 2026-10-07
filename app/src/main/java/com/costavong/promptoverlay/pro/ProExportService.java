package com.costavong.promptoverlay.pro;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;

public final class ProExportService extends Service {
    public static final String UPDATE="com.costavong.promptoverlay.PRO_JOB_UPDATE";
    public static volatile boolean busy=false;
    public static volatile String stage="",error="",output="",projectId="",kind="";
    public static volatile int percent=0;
    private volatile boolean cancelled=false;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final int NOTIFICATION=71;
    @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("pro-render","Video editor progress",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}if("cancel".equals(intent.getAction())){cancelled=true;return START_NOT_STICKY;}if(busy)return START_NOT_STICKY;
        busy=true;cancelled=false;percent=0;error="";output="";stage="Starting";kind=intent.getStringExtra("kind");projectId=intent.getStringExtra("project");
        int type=Build.VERSION.SDK_INT>=35&& !"captions".equals(kind)?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(),type);else startForeground(NOTIFICATION,notification());
        String snapshot=intent.getStringExtra("snapshot_file"),clip=intent.getStringExtra("clip"),language=intent.getStringExtra("language");int resolution=intent.getIntExtra("resolution",720);
        worker.execute(()->{try{
            File snapshotFile=new File(snapshot);ProProject project=ProProject.from(new JSONObject(new String(java.nio.file.Files.readAllBytes(snapshotFile.toPath()),java.nio.charset.StandardCharsets.UTF_8)));snapshotFile.delete();
            ProAudio.Control control=new ProAudio.Control(){public void check()throws Exception{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Cancelled.");}
                long updated=0;public void progress(String message,int value){stage=message;percent=value;long now=SystemClock.elapsedRealtime();if(now-updated>300||value==100){updated=now;publish();}}};
            if("captions".equals(kind))ProCaptions.generate(this,project,clip,language,control);
            else{File exports=new File(getFilesDir(),"pro/exports");exports.mkdirs();File destination=new File(exports,project.id+"-r"+project.revision+"-"+resolution+"p.mp4");
                if(!destination.exists())ProRenderEngine.render(project,resolution,destination,new File(getCacheDir(),"render-"+project.id),control);output=destination.getPath();}
            stage="captions".equals(kind)?"Captions ready":"Video ready";percent=100;
        }catch(Throwable problem){error=problem.getMessage()==null?problem.getClass().getSimpleName():problem.getMessage();stage="Stopped";}
        finally{busy=false;publish();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});return START_NOT_STICKY;
    }
    private Notification notification(){Intent open=new Intent(this,ProActivity.class);open.putExtra("project",projectId);
        PendingIntent content=PendingIntent.getActivity(this,71,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,72,new Intent(this,ProExportService.class).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"pro-render").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Prompt Overlay Pro").setContentText(ProStrings.t(this,stage)+" · "+percent+"%")
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,percent,false).addAction(new Notification.Action.Builder(null,"Cancel",cancel).build()).build();}
    private void publish(){if(busy)getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));}
    @Override public void onTimeout(int startId,int type){cancelled=true;error="Android stopped the long-running job. Try exporting shorter sections.";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){cancelled=true;worker.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
