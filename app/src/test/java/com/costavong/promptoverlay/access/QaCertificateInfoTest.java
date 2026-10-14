package com.costavong.promptoverlay.access;

import org.junit.Test;
import static org.junit.Assert.*;

public final class QaCertificateInfoTest {
    @Test public void sha1FormattingIsUppercaseColonSeparated(){
        byte[] bytes=new byte[20];
        for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*13);
        assertEquals(
            "00:0D:1A:27:34:41:4E:5B:68:75:82:8F:9C:A9:B6:C3:D0:DD:EA:F7",
            QaCertificateInfo.formatSha1(bytes));
    }

    @Test public void cannotFormatWrongSizedDigest(){
        try{QaCertificateInfo.formatSha1(new byte[19]);fail();}
        catch(IllegalArgumentException expected){}
    }

    @Test public void fingerprintDerivationIsDeterministic(){
        String first=QaCertificateInfo.fromSigningBytes(new byte[]{1,2,3,4});
        String second=QaCertificateInfo.fromSigningBytes(new byte[]{1,2,3,4});
        assertEquals(first,second);
        assertTrue(first.matches("([0-9A-F]{2}:){19}[0-9A-F]{2}"));
        assertNotEquals(first,QaCertificateInfo.fromSigningBytes(new byte[]{1,2,3,5}));
    }
}
