package com.costavong.promptoverlay.pro;

import android.graphics.Bitmap;
import android.opengl.*;
import android.view.Surface;
import java.nio.*;

/** EGL encoder input with explicit frame presentation timestamps. */
final class ProGlSurface implements AutoCloseable {
    private EGLDisplay display;private EGLContext context;private EGLSurface surface;
    private int program,texture;private final FloatBuffer vertices,uv;
    private static FloatBuffer buffer(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).position(0);return b;}
    ProGlSurface(Surface target)throws Exception{
        vertices=buffer(new float[]{-1,-1,1,-1,-1,1,1,1});uv=buffer(new float[]{0,1,1,1,0,0,1,0});
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("Cannot initialize the video encoder display.");
        int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0);
        if(count[0]==0)throw new IllegalStateException("No compatible video encoder surface.");
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreateWindowSurface(display,configs[0],target,new int[]{EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("Cannot activate video encoder surface.");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 aPos;attribute vec2 aUv;varying vec2 vUv;void main(){vUv=aUv;gl_Position=vec4(aPos,0.,1.);}"));
        GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float;varying vec2 vUv;uniform sampler2D image;void main(){gl_FragColor=texture2D(image,vUv);}"));
        GLES20.glLinkProgram(program);int[] status=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);
        if(status[0]!=GLES20.GL_TRUE)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    }
    private int shader(int type,String source){int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
        int[] status=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(id));return id;}
    void draw(Bitmap bitmap,long timeUs){if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("Cannot activate the encoder surface.");GLES20.glViewport(0,0,bitmap.getWidth(),bitmap.getHeight());GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
        int pos=GLES20.glGetAttribLocation(program,"aPos"),coord=GLES20.glGetAttribLocation(program,"aUv");
        GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
        GLES20.glEnableVertexAttribArray(coord);GLES20.glVertexAttribPointer(coord,2,GLES20.GL_FLOAT,false,0,uv);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        EGLExt.eglPresentationTimeANDROID(display,surface,timeUs*1000);
        if(!EGL14.eglSwapBuffers(display,surface))throw new IllegalStateException("Video encoder rejected a frame.");
    }
    @Override public void close(){if(display!=null){GLES20.glDeleteProgram(program);GLES20.glDeleteTextures(1,new int[]{texture},0);
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglReleaseThread();EGL14.eglTerminate(display);}}
}
