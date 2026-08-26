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

/** R177 diagnostics with verified Fit Test/Self Test wire IDs. */
public final class Ayf1DiagnosticsActivity extends Activity {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int FIT=157,FIT_RESULT=158,SELF=171,SPATIAL=194;
    private BluetoothSocket socket; private OutputStream out; private volatile boolean connected;
    private final ExecutorService io=Executors.newSingleThreadExecutor(); private TextView result,log;
    private BluetoothDevice device;
    @Override public void onCreate(Bundle b){super.onCreate(b);build();}
    private Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    private void build(){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(20,20,20,20);
        r.addView(label("AYF1 / R177 DIAGNOSTICS",22)); result=label("Bağlantı bekleniyor",15);r.addView(result);
        Button connect=btn("SM-R177 BAĞLAN");connect.setOnClickListener(v->connect());r.addView(connect);
        LinearLayout fit=new LinearLayout(this);Button start=btn("FIT TEST BAŞLAT"),stop=btn("FIT TEST DURDUR");
        start.setOnClickListener(v->send(FIT,new byte[]{1}));stop.setOnClickListener(v->send(FIT,new byte[]{0}));fit.addView(start,new LinearLayout.LayoutParams(0,-2,1));fit.addView(stop,new LinearLayout.LayoutParams(0,-2,1));r.addView(fit);
        Button self=btn("SELF TEST ÇALIŞTIR");self.setOnClickListener(v->send(SELF,new byte[0]));r.addView(self);
        Button spatial=btn("SPATIAL / GYRO RX MONITOR");spatial.setOnClickListener(v->append("Spatial RX monitor hazır (MSG 194)."));r.addView(spatial);
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
    private void readLoop(BluetoothSocket s){try{InputStream in=s.getInputStream();byte[] b=new byte[1024];while(connected){int n=in.read(b);if(n<0)break;byte[] x=Arrays.copyOf(b,n);append("RX "+hex(x));decode(x);}}catch(Exception e){append("READ_END "+e);}finally{connected=false;}}
    private void decode(byte[] raw){for(int i=0;i<raw.length-6;i++){if((raw[i]&255)!=0xFD)continue;for(int j=i+6;j<raw.length;j++)if((raw[j]&255)==0xDD){byte[] f=Arrays.copyOfRange(raw,i,j+1);if(!crc(f))break;int id=f[3]&255;byte[] p=Arrays.copyOfRange(f,4,f.length-3);if(id==FIT_RESULT&&p.length>=2){String l=fitText(p[0]&255),rr=fitText(p[1]&255);runOnUiThread(()->result.setText("FIT TEST — Sol: "+l+" / Sağ: "+rr));}else if(id==SELF&&p.length>=4){int bits=(p[0]&255)|((p[1]&255)<<8)|((p[2]&255)<<16)|((p[3]&255)<<24);runOnUiThread(()->result.setText("SELF TEST bitmap: 0x"+String.format(Locale.US,"%08X",bits)+" / allChecks="+(bits==0)));}else if(id==SPATIAL){append("SPATIAL EVENT "+(p.length>0?(p[0]&255):-1)+" DATA "+hex(p));}break;}}}
    private String fitText(int v){return v==0?"KÖTÜ":v==1?"İYİ":v==2?"BAŞARISIZ":"BİLİNMİYOR("+v+")";}
    private void send(int id,byte[] p){if(!connected||out==null){toast("Önce bağlan");return;}byte[] f=frame(id,p);io.execute(()->{try{out.write(f);out.flush();append("TX "+hex(f));}catch(Exception e){append("TX_ERROR "+e);}});}
    private byte[] frame(int id,byte[] p){int declared=1+p.length+2;byte[] f=new byte[declared+4];f[0]=(byte)0xFD;f[1]=(byte)declared;f[2]=(byte)(declared>>8);f[3]=(byte)id;System.arraycopy(p,0,f,4,p.length);int c=crc16(f,1,declared);int q=f.length-3;f[q]=(byte)c;f[q+1]=(byte)(c>>8);f[q+2]=(byte)0xDD;return f;}
    private boolean crc(byte[] f){if(f.length<7)return false;int q=f.length-3;return ((f[q]&255)|((f[q+1]&255)<<8))==crc16(f,1,q-1);}
    private int crc16(byte[] b,int o,int n){int c=0xFFFF;for(int i=o;i<o+n;i++){c^=(b[i]&255)<<8;for(int k=0;k<8;k++)c=(c&0x8000)!=0?((c<<1)^0x1021)&65535:(c<<1)&65535;}return c;}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b){if(s.length()>0)s.append(' ');s.append(String.format(Locale.US,"%02X",x&255));}return s.toString();}
    private void append(String s){runOnUiThread(()->{log.append(s);log.append("\n");});}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){connected=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}io.shutdownNow();super.onDestroy();}
}
