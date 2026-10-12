package com.costavong.promptoverlay.pro;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Limits evidence attached through email and validates optional shared links.
 * All feedback is explicitly sent by the user via an external mail client.
 */
public final class FeedbackEvidencePolicy {
    public static final int MAX_FILES=4;
    public static final long MAX_TOTAL_BYTES=15L*1024L*1024L;

    private FeedbackEvidencePolicy(){}

    public static boolean isImageOrVideo(String mime){
        if(mime==null)return false;
        String type=mime.toLowerCase(Locale.ROOT);
        return type.startsWith("image/")||type.startsWith("video/");
    }

    public static boolean canAttach(int files,long totalBytes,long nextBytes){
        return files>=0&&files<MAX_FILES&&totalBytes>=0
            &&nextBytes>0&&nextBytes<=MAX_TOTAL_BYTES-totalBytes;
    }

    /** Empty is allowed; non-empty links must have an http(s) origin. */
    public static String normalizeLink(String raw){
        if(raw==null||raw.trim().isEmpty())return "";
        String value=raw.trim();
        if(value.length()>2048||value.contains("\n")||value.contains("\r")
            ||value.matches(".*\\s+.*"))throw new IllegalArgumentException("Invalid link");
        if(!value.contains("://"))value="https://"+value;
        try{
            URI uri=new URI(value);
            String scheme=uri.getScheme(),host=uri.getHost();
            if(scheme==null||host==null||host.trim().isEmpty())throw new IllegalArgumentException("Invalid link");
            if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))
                throw new IllegalArgumentException("Invalid link");
            if(uri.getUserInfo()!=null)throw new IllegalArgumentException("Invalid link");
            return value;
        }catch(URISyntaxException e){throw new IllegalArgumentException("Invalid link",e);}
    }
}
