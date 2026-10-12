package com.costavong.promptoverlay.access;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores only the short-lived server session token, encrypted with an
 * AndroidKeyStore non-exportable AES key. No paid/trial rights are ever
 * stored or inferred from local preferences.
 */
public final class TrialSessionVault {
    private static final String ALIAS="prompt_overlay_trial_session_v1";
    private static final String PREFS="prompt_overlay_trial_identity";
    private static final String KEY="encrypted_server_session";
    private final Context context;
    public TrialSessionVault(Context context){this.context=context.getApplicationContext();}

    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        SecretKey existing=(SecretKey)store.getKey(ALIAS,null);
        if(existing!=null)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }
    private SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    public void save(String token)throws Exception{
        if(token==null||token.length()<40)throw new IllegalArgumentException("Missing session");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();
        byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] bytes=new byte[iv.length+encrypted.length];
        System.arraycopy(iv,0,bytes,0,iv.length);
        System.arraycopy(encrypted,0,bytes,iv.length,encrypted.length);
        if(!prefs().edit().putString(KEY,Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())
            throw new IllegalStateException("Unable to save session");
    }
    public String restore(){
        String encoded=prefs().getString(KEY,null);
        if(encoded==null)return null;
        try{
            byte[] bytes=Base64.decode(encoded,Base64.DEFAULT);
            if(bytes.length<12+16)throw new IllegalStateException("Corrupted saved session");
            byte[] iv=new byte[12];System.arraycopy(bytes,0,iv,0,12);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            String token=new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
            if(!token.matches("[A-Za-z0-9_-]{40,80}"))throw new IllegalStateException("Bad token");
            return token;
        }catch(Exception invalid){
            clear();return null;
        }
    }
    public void clear(){prefs().edit().remove(KEY).apply();}
}
