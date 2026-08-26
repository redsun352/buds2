package com.yigit.buds2blescanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Functional AYF1 control surface using only documented/verified wire formats. */
public class Ayf1ControlsActivity extends Activity {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter adapter; private BluetoothDevice selected; private BluetoothSocket socket;
    private OutputStream out; private volatile boolean connected;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final StringBuilder log=new StringBuilder(); private TextView status,logView; private Spinner spinner;
    private final ArrayList<BluetoothDevice> devices=new ArrayList<>();

    @Override public void onCreate(Bundle b){super.onCreate(b);BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=bm==null?null:bm.getAdapter();buildUi();if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},7001);else loadBonded();}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);} private TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;} private Button b(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);return x;}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(8),dp(10),dp(8));root.addView(t("AYF1 — LIVE CONTROLS",24));status=t("● Bağlı değil",13);root.addView(status);
        LinearLayout pick=new LinearLayout(this);spinner=new Spinner(this);pick.addView(spinner,new LinearLayout.LayoutParams(0,-2,1));Button ref=b("YENİLE");ref.setOnClickListener(v->loadBonded());pick.addView(ref);root.addView(pick);
        LinearLayout con=new LinearLayout(this);Button connect=b("BAĞLAN"),disconnect=b("KES");connect.setOnClickListener(v->connect());disconnect.setOnClickListener(v->disconnect());con.addView(connect,new LinearLayout.LayoutParams(0,-2,1));con.addView(disconnect,new LinearLayout.LayoutParams(0,-2,1));root.addView(con);
        root.addView(t("AMBIENT",18));LinearLayout am=new LinearLayout(this);Button on=b("AÇ"),off=b("KAPAT");on.setOnClickListener(v->send(0x80,new byte[]{1}));off.setOnClickListener(v->send(0x80,new byte[]{0}));am.addView(on,new LinearLayout.LayoutParams(0,-2,1));am.addView(off,new LinearLayout.LayoutParams(0,-2,1));root.addView(am);
        LinearLayout av=new LinearLayout(this);for(int i=1;i<=5;i++){final int n=i;Button x=b("VOL "+i);x.setOnClickListener(v->send(0x84,new byte[]{(byte)n}));av.addView(x,new LinearLayout.LayoutParams(0,-2,1));}root.addView(av);
        root.addView(t("EQUALIZER",18));LinearLayout eq=new LinearLayout(this);String[] names={"Bass","Soft","Dynamic","Clear","Treble"};int[] ids={0,1,2,3,4};for(int i=0;i<names.length;i++){final int id=ids[i];Button x=b(names[i]);x.setOnClickListener(v->send(0x86,new byte[]{1,(byte)id}));eq.addView(x,new LinearLayout.LayoutParams(0,-2,1));}root.addView(eq);Button eqOff=b("EQ KAPAT");eqOff.setOnClickListener(v->send(0x86,new byte[]{0,0}));root.addView(eqOff);
        root.addView(t("TOUCH",18));LinearLayout tl=new LinearLayout(this);Button lock=b("KİLİTLE"),unlock=b("AÇ");lock.setOnClickListener(v->send(0x90,new byte[]{1}));unlock.setOnClickListener(v->send(0x90,new byte[]{0}));tl.addView(lock,new LinearLayout.LayoutParams(0,-2,1));tl.addView(unlock,new LinearLayout.LayoutParams(0,-2,1));root.addView(tl);
        LinearLayout to=new LinearLayout(this);String[] names2={"Asistan","Quick Ambient","Ses","Ambient"};int[] vals={0,1,2,3};for(int i=0;i<4;i++){final int n=vals[i];Button x=b(names2[i]);x.setOnClickListener(v->send(0x92,new byte[]{(byte)n,(byte)n}));to.addView(x,new LinearLayout.LayoutParams(0,-2,1));}root.addView(to);
        root.addView(t("FIND MY EARBUDS",18));LinearLayout fm=new LinearLayout(this);Button fs=b("BAŞLAT"),fe=b("DURDUR");fs.setOnClickListener(v->send(0xA0,new byte[0]));fe.setOnClickListener(v->send(0xA1,new byte[0]));fm.addView(fs,new LinearLayout.LayoutParams(0,-2,1));fm.addView(fe,new LinearLayout.LayoutParams(0,-2,1));root.addView(fm);
        root.addView(t("MANAGER / TIME",18));LinearLayout mi=new LinearLayout(this);Button info=b("MANAGER INFO"),time=b("SAAT");info.setOnClickListener(v->send(0x88,new byte[]{1,1,(byte)android.os.Build.VERSION.SDK_INT}));time.setOnClickListener(v->send(0xA7,timePayload()));mi.addView(info,new LinearLayout.LayoutParams(0,-2,1));mi.addView(time,new LinearLayout.LayoutParams(0,-2,1));root.addView(mi);
        root.addView(t("ANC / ADVANCED",18));Button anc=b("ANC KONTROLÜ — CAPTURE GEREKİYOR");anc.setOnClickListener(v->toast("ANC/advanced payloadı bu R177 AYF1 capture setinde TX olarak doğrulanmadı; güvenli gönderim kilitli."));root.addView(anc);
        root.addView(t("DIAGNOSTICS",18));root.addView(t("Self-test, fit-test, ANC leak ve mic loopback AYF1'de mevcut. TX payloadları doğrulanınca aynı panele bağlanacak.",12));
        logView=t("TX/RX log\n",11);logView.setTextIsSelectable(true);ScrollView sv=new ScrollView(this);sv.addView(logView);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void loadBonded(){if(adapter==null)return;if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;devices.clear();ArrayList<String> names=new ArrayList<>();for(BluetoothDevice d:adapter.getBondedDevices()){devices.add(d);names.add(safeName(d)+"  "+d.getAddress());}spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));if(names.isEmpty())toast("Buds2'yi Android Bluetooth ayarlarından eşleştir.");}
    private String safeName(BluetoothDevice d){try{return d.getName()==null?"Bluetooth device":d.getName();}catch(Exception e){return"Bluetooth device";}}
    private void connect(){if(devices.isEmpty()){toast("Eşleşmiş cihaz yok");return;}selected=devices.get(spinner.getSelectedItemPosition());io.execute(()->{try{BluetoothSocket s=selected.createRfcommSocketToServiceRecord(SPP);adapter.cancelDiscovery();s.connect();socket=s;out=s.getOutputStream();connected=true;runOnUiThread(()->status.setText("● RFCOMM BAĞLI — "+safeName(selected)));append("CONNECTED "+selected.getAddress());readLoop(s);}catch(Exception e){connected=false;runOnUiThread(()->status.setText("● Bağlantı hatası: "+e.getClass().getSimpleName()));append("CONNECT_ERROR "+e);}});}
    private void readLoop(BluetoothSocket s){try{InputStream in=s.getInputStream();byte[] buf=new byte[1024];while(connected){int n=in.read(buf);if(n<0)break;append("RX "+hex(Arrays.copyOf(buf,n)));}}catch(Exception e){append("READ_END "+e);}finally{connected=false;runOnUiThread(()->status.setText("● Bağlantı kesildi"));}}
    private void disconnect(){connected=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}append("DISCONNECTED");}
    private void send(int id,byte[] payload){if(!connected||out==null){toast("Önce Buds2'ye bağlan");return;}byte[] frame=frame(id,payload);io.execute(()->{try{out.write(frame);out.flush();append("TX "+hex(frame));}catch(Exception e){append("TX_ERROR "+e);}});}
    private byte[] frame(int id,byte[] payload){int declared=1+payload.length+2;byte[] b=new byte[declared+4];b[0]=(byte)0xFD;b[1]=(byte)declared;b[2]=(byte)(declared>>8);b[3]=(byte)id;System.arraycopy(payload,0,b,4,payload.length);int crc=crc16(b,3,declared-2);int p=b.length-3;b[p]=(byte)crc;b[p+1]=(byte)(crc>>8);b[p+2]=(byte)0xDD;return b;}
    private int crc16(byte[] b,int off,int len){int c=0;for(int i=off;i<off+len;i++){c^=(b[i]&255)<<8;for(int j=0;j<8;j++)c=((c&0x8000)!=0)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff;}return c;}
    private byte[] timePayload(){long now=System.currentTimeMillis();int tz=TimeZone.getDefault().getOffset(now);return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(now).putInt(tz).array();}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b){if(s.length()>0)s.append(' ');s.append(String.format(Locale.US,"%02X",x&255));}return s.toString();}
    private void append(String s){runOnUiThread(()->{log.append(new java.text.SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())).append(" ").append(s).append('\n');if(log.length()>12000)log.delete(0,3000);logView.setText(log.toString());});}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){disconnect();io.shutdownNow();super.onDestroy();}
}
