package com.yigit.buds2blescanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Galaxy Buds2 SPP/RFCOMM protocol analyzer.
 *
 * The Android app keeps the transport passive: it does not send control
 * commands merely by connecting. The decoder follows the Buds protocol
 * model used by GalaxyBudsClient while preserving the FD/DD framing observed
 * in Buds2 captures from this application.
 *
 * Wire frame used by the current Buds2 captures:
 *   FD | uint16 LE header | message id | payload | CRC16 | DD
 *
 * Header low 10 bits = message-id/payload/CRC size. Total frame length is
 * declaredSize + 4. FD/DD are not searched as payload delimiters.
 */
public final class BudsProtocolAnalyzer {
    public static final class Frame {
        public final byte[] bytes;
        public final int length, start, end, header, declaredSize, messageId;
        public final boolean response, fragment;
        public final int payloadLength, crcReceived, crcCalculated;
        public final boolean crcValid;
        public final int[] variableOffsets;
        public final String checksumNote, shapeKey, diffNote, messageName, decodedInfo;

        Frame(byte[] b, int h, int id, boolean resp, boolean frag, CrcResult c,
              int[] vars, String shape, String diff, String name, String decoded) {
            bytes=b; length=b.length; start=b[0]&255; end=b[b.length-1]&255;
            header=h; declaredSize=h&0x03ff; messageId=id;
            response=resp; fragment=frag; payloadLength=Math.max(0,declaredSize-3);
            crcReceived=c.received; crcCalculated=c.calculated; crcValid=c.valid;
            variableOffsets=vars; checksumNote=c.note; shapeKey=shape;
            diffNote=diff; messageName=name; decodedInfo=decoded;
        }

        public String summary() {
            StringBuilder s=new StringBuilder();
            s.append("FRAME len=").append(length)
             .append(" header=").append(hex2(header,4))
             .append(" size=").append(declaredSize)
             .append(" msgId=").append(hex2(messageId,2))
             .append(" (").append(messageName).append(")")
             .append(" payload=").append(payloadLength)
             .append(" response=").append(response)
             .append(" fragment=").append(fragment)
             .append(" shape=").append(shapeKey)
             .append(" checksum=").append(checksumNote);
            if(decodedInfo!=null&&!decodedInfo.isEmpty()) s.append(" decode=").append(decodedInfo);
            if(diffNote!=null&&!diffNote.isEmpty()) s.append(" diff=").append(diffNote);
            return s.toString();
        }
    }

    private final ArrayList<Byte> pending=new ArrayList<>();
    private final ArrayList<Frame> history=new ArrayList<>();
    private int frameNumber;

    public synchronized void reset(){ pending.clear(); history.clear(); frameNumber=0; }
    public synchronized int frameCount(){ return frameNumber; }

    /** Accept arbitrary RFCOMM read chunks, including partial/multiple frames. */
    public synchronized List<Frame> feed(byte[] chunk){
        ArrayList<Frame> out=new ArrayList<>();
        if(chunk==null||chunk.length==0)return out;
        for(byte b:chunk)pending.add(b);

        while(true){
            int fd=indexOf(0xFD,0);
            if(fd<0){pending.clear();break;}
            if(fd>0)pending.subList(0,fd).clear();
            if(pending.size()<3)break;

            int header=(pending.get(1)&255)|((pending.get(2)&255)<<8);
            int declared=header&0x03ff;
            int total=declared+4;
            if(declared<3||total>4096){pending.remove(0);continue;}
            if(pending.size()<total)break;

            // Never terminate on the first DD. DD may occur inside payload.
            if((pending.get(total-1)&255)!=0xDD){
                // A corrupt candidate: resynchronise at the next FD, otherwise
                // discard one byte. This is deliberately length-driven.
                int next=indexOf(0xFD,1);
                if(next>0){pending.subList(0,next).clear();continue;}
                pending.remove(0);continue;
            }

            byte[] b=new byte[total];
            for(int i=0;i<total;i++)b[i]=pending.get(i);
            pending.subList(0,total).clear();

            int id=b[3]&255;
            boolean resp=(header&0x1000)!=0;
            boolean frag=(header&0x2000)!=0;
            CrcResult crc=crc(b);
            String shape=String.format(Locale.US,"ID%02X-L%d",id,total);
            Frame previous=previous(shape);
            String diff=diff(b,previous);
            int[] vars=variables(b,previous);
            Frame f=new Frame(b,header,id,resp,frag,crc,vars,shape,diff,
                    messageName(id),decode(id,b));
            history.add(f);frameNumber++;out.add(f);
        }
        return out;
    }

    private int indexOf(int v,int from){
        for(int i=Math.max(0,from);i<pending.size();i++)if((pending.get(i)&255)==v)return i;
        return -1;
    }
    private Frame previous(String shape){
        for(int i=history.size()-1;i>=0;i--)if(history.get(i).shapeKey.equals(shape))return history.get(i);
        return null;
    }
    private int[] variables(byte[] a,Frame p){
        if(p==null||p.bytes.length!=a.length)return new int[0];
        ArrayList<Integer> x=new ArrayList<>();
        int end=Math.max(4,a.length-3);
        for(int i=0;i<end;i++)if(a[i]!=p.bytes[i])x.add(i);
        int[] r=new int[x.size()];for(int i=0;i<r.length;i++)r[i]=x.get(i);return r;
    }
    private String diff(byte[] a,Frame p){
        if(p==null||p.bytes.length!=a.length)return "none";
        StringBuilder s=new StringBuilder();int n=0;int end=Math.max(4,a.length-3);
        for(int i=0;i<end;i++)if(a[i]!=p.bytes[i]){
            if(n++>0)s.append(',');
            s.append(i).append(':').append(hex2(p.bytes[i]&255,2)).append('>').append(hex2(a[i]&255,2));
            if(n>=32){s.append(",...");break;}
        }
        return n==0?"identical_payload":s.toString();
    }

    private static final class CrcResult{
        final int received,calculated;final boolean valid;final String note;
        CrcResult(int r,int c,boolean v,String n){received=r;calculated=c;valid=v;note=n;}
    }
    private static CrcResult crc(byte[] b){
        if(b.length<8)return new CrcResult(-1,-1,false,"too_short");
        int off=b.length-3;
        int received=(b[off]&255)|((b[off+1]&255)<<8);
        int calculated=crc16(b,3,off-3);
        boolean ok=received==calculated;
        String note=ok?String.format(Locale.US,"CRC16_CCITT_LE_OK=0x%04X",calculated)
                :String.format(Locale.US,"CRC_FAIL recvLE=0x%04X calc=0x%04X",received,calculated);
        return new CrcResult(received,calculated,ok,note);
    }
    private static int crc16(byte[] b,int off,int len){
        int crc=0;
        for(int i=off;i<off+len;i++){
            crc^=(b[i]&255)<<8;
            for(int j=0;j<8;j++)crc=((crc&0x8000)!=0)?((crc<<1)^0x1021)&0xffff:(crc<<1)&0xffff;
        }
        return crc&0xffff;
    }

    private static String messageName(int id){
        switch(id){
            case 0x41:return "METERING_REPORT";
            case 0x51:return "RESP";
            case 0x60:return "STATUS_UPDATED";
            case 0x61:return "EXTENDED_STATUS_UPDATED";
            case 0x62:return "CONNECTION_UPDATED";
            case 0x63:return "VERSION_INFO";
            case 0x6c:return "PAUSE_MEDIA_WHEN_ONE_BUD_REMOVED";
            case 0x6f:return "SET_ANC_WITH_ONE_EARBUD";
            case 0x70:return "MAIN_CHANGE";
            case 0x71:return "PROFILE_CONTROL";
            case 0x72:return "PAIRING_MODE";
            case 0x77:return "MULTIPOINT_INFO";
            case 0x78:return "NOISE_CONTROLS_UPDATE";
            case 0x79:return "NOISE_CONTROLS";
            case 0x7b:return "SET_DETECT_CONVERSATIONS";
            case 0x7c:return "SET_DETECT_CONVERSATIONS_DURATION";
            case 0x7d:return "SET_SPATIAL_AUDIO";
            case 0x80:return "SET_AMBIENT_MODE";
            case 0x81:return "AMBIENT_MODE_UPDATED";
            case 0x82:return "CUSTOMIZE_AMBIENT_SOUND";
            case 0x83:return "NOISE_REDUCTION_LEVEL";
            case 0x84:return "AMBIENT_VOLUME";
            case 0x85:return "ADJUST_SOUND_SYNC";
            case 0x86:return "EQUALIZER";
            case 0x87:return "GAME_MODE";
            case 0x88:return "MANAGER_INFO";
            case 0x8b:return "SET_SIDETONE";
            case 0x90:return "LOCK_TOUCHPAD";
            case 0x91:return "TOUCH_UPDATED";
            case 0x92:return "SET_TOUCHPAD_OPTION";
            case 0x93:return "SET_TOUCHPAD_OTHER_OPTION";
            case 0x94:return "BATTERY_TYPE";
            case 0x9f:return "PASS_THROUGH";
            case 0xa0:return "FIND_MY_EARBUDS_START";
            case 0xa1:return "FIND_MY_EARBUDS_STOP";
            case 0xa2:return "MUTE_EARBUD";
            case 0xa3:return "MUTE_EARBUD_STATUS_UPDATED";
            case 0xa7:return "UPDATE_TIME";
            case 0xab:return "SELF_TEST";
            case 0xb0:return "FOTA_V1_SESSION";
            case 0xb1:return "FOTA_V1_CONTROL";
            case 0xb2:return "FOTA_V1_DOWNLOAD_DATA";
            case 0xb3:return "FOTA_V1_UPDATED";
            case 0xf1:return "DEBUG_ERROR_CODE";
            case 0xf2:return "DEBUG_EVENT";
            default:return String.format(Locale.US,"UNKNOWN_0x%02X",id);
        }
    }

    private static String decode(int id,byte[] b){
        int ps=4,pe=b.length-3;if(pe<=ps)return "";
        switch(id){
            case 0x60:
                if(pe-ps>=6)return String.format(Locale.US,"ear=%d batL=%d%% batR=%d%% coupled=%d main=%d wearing=%d",u8(b,ps),u8(b,ps+1),u8(b,ps+2),u8(b,ps+3),u8(b,ps+4),u8(b,ps+5));
                break;
            case 0x61:
                if(pe-ps>=12)return String.format(Locale.US,"MR=%d ear=%d batL=%d%% batR=%d%% coupled=%d main=%d wearing=%d ambient=%d voiceFocus=%d ambientVolRaw=%d eq=%d eqType=%d",u8(b,ps),u8(b,ps+1),u8(b,ps+2),u8(b,ps+3),u8(b,ps+4),u8(b,ps+5),u8(b,ps+6),u8(b,ps+7),u8(b,ps+8),u8(b,ps+9),u8(b,ps+10),u8(b,ps+11));
                break;
            case 0x51:
                if(pe-ps>=2)return "action="+hex2(u8(b,ps),2)+" result="+hex2(u8(b,ps+1),2);
                break;
            case 0x63:return "raw="+hexRange(b,ps,pe);
            case 0x78:case 0x79:case 0x80:case 0x81:case 0x86:case 0x87:case 0x88:case 0x90:case 0x91:
                return "payload="+hexRange(b,ps,pe);
            default:break;
        }
        return "payload="+hexRange(b,ps,pe);
    }
    private static int u8(byte[] b,int i){return b[i]&255;}
    private static String hex2(int n,int width){return String.format(Locale.US,"%0"+width+"X",n);}
    private static String hexRange(byte[] b,int s,int e){
        StringBuilder x=new StringBuilder();for(int i=s;i<e;i++){if(i>s)x.append(' ');x.append(hex2(b[i]&255,2));}return x.toString();
    }
}