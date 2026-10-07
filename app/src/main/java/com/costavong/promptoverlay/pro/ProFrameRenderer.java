package com.costavong.promptoverlay.pro;

import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.text.*;
import android.view.View;
import java.io.*;
import java.util.*;

/** Render all four logical tracks into one frame at an explicit master timestamp. */
public final class ProFrameRenderer implements AutoCloseable {
    private final ProProject project;
    private final ProTimeline timeline;
    private final int width,height;
    private final Map<String,MediaMetadataRetriever> retrievers=new LinkedHashMap<>(4,.75f,true);
    private final Map<String,Bitmap> images=new LinkedHashMap<>(4,.75f,true);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Bitmap output,clipFrame;
    private final Canvas outCanvas,clipCanvas;
    private final int[] greenPixels;
    private final Bitmap greenComposite;
    private final Canvas greenCanvas;
    public ProFrameRenderer(ProProject p,int w,int h)throws Exception{
        project=p;timeline=p.timeline();width=w;height=h;
        output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);outCanvas=new Canvas(output);
        clipFrame=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);clipCanvas=new Canvas(clipFrame);
        boolean hasGreen=false;
        for(ProProject.Clip c:p.clips)hasGreen|=c.green;
        greenPixels=hasGreen?new int[w*h]:null;
        greenComposite=hasGreen?Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888):null;
        greenCanvas=hasGreen?new Canvas(greenComposite):null;
    }
    public Bitmap render(long timeUs)throws Exception{
        output.eraseColor(Color.BLACK);
        List<ProTimeline.Entry> active=timeline.at(timeUs);
        for(int i=0;i<active.size();i++){
            ProTimeline.Entry e=active.get(i);ProProject.Clip c=project.clip(e.id);
            MediaMetadataRetriever retriever=retriever(c.path);
            Bitmap source=android.os.Build.VERSION.SDK_INT>=27
                ?retriever.getScaledFrameAtTime(e.sourceAt(timeUs),MediaMetadataRetriever.OPTION_CLOSEST,width,height)
                :retriever.getFrameAtTime(e.sourceAt(timeUs),MediaMetadataRetriever.OPTION_CLOSEST);
            if(source==null)throw new IOException("Cannot decode a video frame in "+c.name);
            try{
                clipFrame.eraseColor(Color.TRANSPARENT);paint.reset();paint.setFlags(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                ColorMatrix saturation=new ColorMatrix();saturation.setSaturation((float)c.saturation);
                float contrast=(float)c.contrast,offset=(float)(c.brightness*255+128*(1-c.contrast)),warm=(float)c.temperature;
                ColorMatrix grade=new ColorMatrix(new float[]{contrast*(1+warm*.2f),0,0,0,offset,
                    0,contrast,0,0,offset,0,0,contrast*(1-warm*.2f),0,offset,0,0,0,1,0});
                grade.postConcat(saturation);paint.setColorFilter(new ColorMatrixColorFilter(grade));
                Rect crop=new Rect(Math.round(source.getWidth()*c.cropLeft),Math.round(source.getHeight()*c.cropTop),
                    Math.round(source.getWidth()*c.cropRight),Math.round(source.getHeight()*c.cropBottom));
                if(crop.width()<2||crop.height()<2)throw new IOException("The crop area is empty.");
                float scale=Math.max((float)width/crop.width(),(float)height/crop.height());
                float dw=crop.width()*scale,dh=crop.height()*scale;
                clipCanvas.drawBitmap(source,crop,new RectF((width-dw)/2,(height-dh)/2,(width+dw)/2,(height+dh)/2),paint);
                paint.setColorFilter(null);
                if(c.green){
                    clipFrame.getPixels(greenPixels,0,width,0,0,width,height);
                    for(int n=0;n<greenPixels.length;n++){
                        int v=greenPixels[n],r=(v>>16)&255,g=(v>>8)&255,b=v&255;
                        float dominance=(g-Math.max(r,b))/255f;
                        float alpha=1-Math.max(0,Math.min(1,(dominance-c.greenThreshold)/Math.max(.01f,c.greenFeather)));
                        greenPixels[n]=((Math.round(alpha*255)&255)<<24)|(v&0xffffff);
                    }
                    clipFrame.setPixels(greenPixels,0,width,0,0,width,height);
                    Bitmap bg=image(project.greenBackground);
                    greenComposite.eraseColor(Color.BLACK);
                    if(bg!=null)greenCanvas.drawBitmap(bg,null,new Rect(0,0,width,height),paint);
                    greenCanvas.drawBitmap(clipFrame,0,0,paint);
                }
                paint.setAlpha(i==0?255:(int)Math.round(255*timeline.incomingWeight(e,timeUs)));
                outCanvas.drawBitmap(c.green?greenComposite:clipFrame,0,0,paint);paint.setAlpha(255);
            }finally{source.recycle();}
        }
        for(ProProject.Layer l:project.layers)if(l.visible(timeline,timeUs))drawLayer(l);
        return output;
    }
    private MediaMetadataRetriever retriever(String path)throws Exception{
        MediaMetadataRetriever r=retrievers.get(path);if(r!=null)return r;
        if(retrievers.size()>=2){String oldest=retrievers.keySet().iterator().next();retrievers.remove(oldest).release();}
        r=new MediaMetadataRetriever();try{r.setDataSource(path);}catch(Exception e){r.release();throw e;}retrievers.put(path,r);return r;
    }
    private Bitmap image(String path)throws IOException{
        if(path==null||path.isEmpty())return null;
        if(images.containsKey(path))return images.get(path);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);
        if(bounds.outWidth<1)throw new IOException("Cannot read image: "+new File(path).getName());
        int sample=1;while(bounds.outWidth/sample>width||bounds.outHeight/sample>height)sample*=2;
        bounds.inJustDecodeBounds=false;bounds.inSampleSize=sample;Bitmap b=BitmapFactory.decodeFile(path,bounds);
        if(b==null)throw new IOException("Cannot read this image.");
        try{int orientation=new android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
            switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:break;}
            if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}
        }catch(IOException ignored){/* Logos without EXIF keep their native orientation. */}
        if(images.size()>=4){String oldest=images.keySet().iterator().next();images.remove(oldest).recycle();}images.put(path,b);return b;
    }
    private void drawLayer(ProProject.Layer l)throws Exception{
        if("image".equals(l.kind)){
            Bitmap b=image(l.path);if(b==null)return;
            float w=width*l.size,h=w*b.getHeight()/b.getWidth(),x=l.x*width,y=l.y*height;
            paint.reset();paint.setFlags(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setAlpha(Math.round(l.opacity*255));
            outCanvas.drawBitmap(b,null,new RectF(x-w/2,y-h/2,x+w/2,y+h/2),paint);return;
        }
        TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG);tp.setColor(l.color);tp.setAlpha(Math.round(l.opacity*255));
        tp.setTypeface(Typeface.create(Typeface.DEFAULT,l.bold?Typeface.BOLD:Typeface.NORMAL));tp.setTextSize(width*l.size);
        tp.setShadowLayer(width*.002f,0,width*.001f,Color.BLACK);
        float longest=0;for(String line:l.text.split("\n",-1))longest=Math.max(longest,tp.measureText(line));
        int maxWidth=(int)Math.min(width*.88,Math.max(width*.12,longest+width*.03));
        StaticLayout layout=StaticLayout.Builder.obtain(l.text,0,l.text.length(),tp,maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false).setLineSpacing(0,1.08f).build();
        float x=l.x*width-maxWidth/2f,y=l.y*height-layout.getHeight()/2f;
        x=Math.max(0,Math.min(width-maxWidth,x));y=Math.max(0,Math.min(height-layout.getHeight(),y));
        paint.reset();paint.setColor(l.background);paint.setAlpha((int)(Color.alpha(l.background)*l.opacity));
        outCanvas.drawRoundRect(new RectF(x-width*.015f,y-height*.005f,x+maxWidth+width*.015f,y+layout.getHeight()+height*.005f),width*.012f,width*.012f,paint);
        outCanvas.save();outCanvas.translate(x,y);layout.draw(outCanvas);outCanvas.restore();
    }
    @Override public void close(){for(MediaMetadataRetriever r:retrievers.values())try{r.release();}catch(Exception ignored){}
        for(Bitmap b:images.values())b.recycle();output.recycle();clipFrame.recycle();if(greenComposite!=null)greenComposite.recycle();}
}
