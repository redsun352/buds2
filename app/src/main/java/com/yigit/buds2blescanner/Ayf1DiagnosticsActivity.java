package com.yigit.buds2blescanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;

/** R177 diagnostics using the Buds2 SPP framing observed in AYF1 captures. */
public final class Ayf1DiagnosticsActivity extends Activity {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int FIT=157,FIT_RESULT=158,SELF=171,SPATIAL=194;
    private static final int SOM_FD=0xFD,EOM_DD=0xDD,SOM_FE=0xFE,EOM_EE=0xEE;
    private BluetoothSocket socket; private OutputStream out; private volatile boolean connected;
    private volatile int som=SOM_FD,eom=EOM_DD; private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView result,log; private BluetoothDevice device;

    @Override public void onCreate(Bundle b){super.onCreate(b);build();}
    private Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    private void build(){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(20,20,20,20);
        r.addView(label("AYF1 / R177 DIAGNOSTICS",22)); result=label("Bağlantı bekleniyor",15);r.addView(result);
        Button connect=btn("SM-R177 BAĞLAN");connect.setOnClickListener(v->connect());r.addView(connect);
        LinearLayout fit=new LinearLayout(this);Button start=btn("FIT TEST BAŞLAT"),stop=btn("FIT TEST DURDUR");
        start.setOnClickListener(v->send(FIT,new byte[]{1}));stop.setOnClickListener(v->send(FIT,new byte[]{0}));
        fit.addView(start,new LinearLayout.LayoutParams(0,-2,1));fit.addView(stop,new LinearLayout.LayoutParams(0,-2,1));r.addView(fit);
        Button self=btn("SELF TEST ÇALIŞTIR");self.setOnClickListener(v->send(SELF,new byte[0]));r.addView(self);
        Button spatial=btn("SPATIAL / GYRO RX MONITOR");spatial.setOnClickListener(v->{append("Spatial RX monitor aktif (MSG 194).");result.setText("SPATIAL / GYRO MONITOR AKTİF");});r.addView(spatial);
        Button unsafe=btn("ANC / LEAK / MIC PAYLOAD ARAŞTIR");unsafe.setOnClickListener(v->toast("Firmware fonksiyonları bulundu; R177 wire payloadı doğrulanmadan gönderim yapılmıyor."));r.addView(unsafe);
        log=label("LOG\n",11);ScrollView sv=new ScrollView(this);sv.addView(log);r.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);
    }
    private TextView label(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setPadding(8,8,8,8);return t;}

    private void connect(){
        if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},7101);return;}
        BluetoothAdapter a=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();if(a==null){toast("Bluetooth yok");return;}
        for(BluetoothDevice d:a.getBondedDevices()){String n=d.getName();if(n!=null&&n.toLowerCase(Locale.US).contains("buds")){device=d;break;}}
        if(device==null){toast("Eşleşmiş Buds bulunamadı");return;}
        io.execute(()->{try{a.cancelDiscovery();socket=device.createRfcommSocketToServiceRecord(SPP);socket.connect();out=socket.getOutputStream();connected=true;runOnUiThread(()->result.setText("RFCOMM bağlı: "+device.getName()));readLoop(socket);}catch(Exception e){append("CONNECT ERROR "+e);}});
    }

    private void readLoop(BluetoothSocket s){
        try{InputStream in=s.getInputStream();byte[] b=new byte[2048];while(connected){int n=in.read(b);if(n<0)break;byte[] x=Arrays.copyOf(b,n);append("RX "+hex(x));decodeChunk(x);}}
        catch(Exception e){append("READ_END "+e);}finally{connected=false;}
    }

    private void decodeChunk(byte[] raw){
        for(int i=0;i<=raw.length-6;i++){
            int start=raw[i]&255;if(start!=SOM_FD&&start!=SOM_FE)continue;
            if(i+4>raw.length) return;
            int header=(raw[i+1]&255)|((raw[i+2]&255)<<8);
            int size=header&0x03FF; // MsgId + payload + CRC
            int total=1+2+size+1;
            if(size<3||i+total>raw.length) continue;
            int id=raw[i+3]&255;int payloadSize=size-3;
            byte[] payload=Arrays.copyOfRange(raw,i+4,i+4+payloadSize);
            int q=i+4+payloadSize;int wireCrc=(raw[q]&255)|((raw[q+1]&255)<<8);
            byte end=raw[q+2];int expected=crc16(id,payload);
            if((end&255)!=(start==SOM_FD?EOM_DD:EOM_EE)||wireCrc!=expected) continue;
            som=start;eom=end&255;
            append(String.format(Locale.US,"FRAME id=%d payload=%d crc=OK mode=%02X/%02X",id,payloadSize,start,end&255));
            if(id==FIT_RESULT&&payload.length>=2){String l=fitText(payload[0]&255),rr=fitText(payload[1]&255);runOnUiThread(()->result.setText("FIT TEST — Sol: "+l+" / Sağ: "+rr));}
            else if(id==SELF&&payload.length>=4){int bits=(payload[0]&255)|((payload[1]&255)<<8)|((payload[2]&255)<<16)|((payload[3]&255)<<24);runOnUiThread(()->result.setText("SELF TEST bitmap: 0x"+String.format(Locale.US,"%08X",bits)));}
            else if(id==SPATIAL){append("SPATIAL/GYRO DATA "+hex(payload));}
            i+=total-1;
        }
    }

    private String fitText(int v){return v==0?"KÖTÜ":v==1?"İYİ":v==2?"BAŞARISIZ":"BİLİNMİYOR("+v+")";}
    private void send(int id,byte[] p){
        if(!connected||out==null){toast("Önce bağlan");return;}
        byte[] f=frame(id,p);io.execute(()->{try{out.write(f);out.flush();append("TX "+hex(f));}catch(Exception e){append("TX_ERROR "+e);}});
    }

    /** 16-bit SPP header: size = message-id + payload + CRC; CRC covers only ID+payload. */
    private byte[] frame(int id,byte[] p){
        int size=1+p.length+2;int header=size;byte[] f=new byte[size+4];
        f[0]=(byte)som;f[1]=(byte)header;f[2]=(byte)(header>>8);f[3]=(byte)id;System.arraycopy(p,0,f,4,p.length);
        int c=crc16(id,p);int q=4+p.length;f[q]=(byte)c;f[q+1]=(byte)(c>>8);f[q+2]=(byte)eom;return f;
    }
    private int crc16(int id,byte[] p){int c=0xFFFF;c=crcByte(c,id);for(byte x:p)c=crcByte(c,x&255);return c;}
    private int crcByte(int c,int v){c^=(v&255)<<8;for(int k=0;k<8;k++)c=(c&0x8000)!=0?((c<<1)^0x1021)&65535:(c<<1)&65535;return c;}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b){if(s.length()>0)s.append(' ');s.append(String.format(Locale.US,"%02X",x&255));}return s.toString();}
    private void append(String s){runOnUiThread(()->{log.append(s);log.append("\n");});}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){connected=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}io.shutdownNow();super.onDestroy();}
}
