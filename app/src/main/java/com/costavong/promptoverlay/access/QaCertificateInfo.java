package com.costavong.promptoverlay.access;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Displays the SHA-1 of the APK actually installed on this Android device,
 * not an assumed GitHub/Play certificate. SHA-1 here is a public OAuth client
 * registration identifier, not a signing key or a password.
 */
public final class QaCertificateInfo {
    private QaCertificateInfo(){}

    public static String formatSha1(byte[] digest){
        if(digest==null||digest.length!=20)throw new IllegalArgumentException("Expected SHA-1");
        StringBuilder result=new StringBuilder(59);
        for(int i=0;i<digest.length;i++){
            if(i>0)result.append(':');
            int x=digest[i]&0xff;
            result.append(Character.toUpperCase(Character.forDigit(x>>4,16)));
            result.append(Character.toUpperCase(Character.forDigit(x&15,16)));
        }
        return result.toString();
    }

    public static String fromSigningBytes(byte[] certificate){
        if(certificate==null||certificate.length==0)
            throw new IllegalArgumentException("Missing signing certificate");
        try{
            return formatSha1(MessageDigest.getInstance("SHA-1").digest(certificate));
        }catch(Exception e){throw new IllegalStateException("Cannot derive OAuth SHA-1",e);}
    }

    public static String installedSha1(Context context){
        try{
            PackageManager manager=context.getPackageManager();
            String name=context.getPackageName();
            Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28){
                PackageInfo info=manager.getPackageInfo(name,
                    PackageManager.GET_SIGNING_CERTIFICATES);
                if(info.signingInfo==null)return "";
                signatures=info.signingInfo.getApkContentsSigners();
            }else{
                @SuppressWarnings("deprecation")
                PackageInfo info=manager.getPackageInfo(name,PackageManager.GET_SIGNATURES);
                signatures=info.signatures;
            }
            if(signatures==null||signatures.length!=1)return "";
            return fromSigningBytes(signatures[0].toByteArray());
        }catch(Exception e){return "";}
    }
}
