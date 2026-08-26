package com.yigit.buds2blescanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;

/** GalaxyBudsClient-compatible SM-R177/Buds2 control surface. */
public class Ayf1ControlsActivity extends Activity {
    private static final UUID SPP_NEW=UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2");
    private static final int NOISE_CONTROLS=120, TOUCH_HOLD_NOISE=121, DETECT_CONV=122, DETECT_CONV_DURATION=123;
    private static final int SPATIAL_AUDIO=124, AMBIENT_MODE=128, CUSTOMIZE_AMBIENT=130, NOISE_LEVEL=131, AMBIENT_VOLUME=132;
    private static final int EQUALIZER=134, MANAGER_INFO=136, ANC_ONE=111, LOCK_TOUCHPAD=144;
    private static final int FIND_START=160, FIND_STOP=161, UPDATE_TIME=167, FIT=157, SELF_TEST=171, SPATIAL_CONTROL=195;
    private static final int REQ_BT=7001;
    private BluetoothAdapter adapter; private BluetoothDevice selected; private BluetoothSocket socket;
    private OutputStream out; private volatile boolean connected;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final StringBuilder log=new StringBuilder(); private TextView status,logView;
    private Spinner spinner; private final ArrayList<BluetoothDevice> devices=new ArrayList<>();

    @Override public void onCreate(Bundle b){super.onCreate(b);BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=bm==null?null:bm.getAdapter();buildUi();requestBluetoothPermissionIfNeeded();}
    private boolean btPermission(){return android.os.Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private void requestBluetoothPermissionIfNeeded(){if(android.os.Build.VERSION.SDK_INT>=31&&!btPermission())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);else loadBonded();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==REQ_BT){if(btPermission())loadBonded();else {status.setText("● Bluetooth erişimi verilmedi");toast("Ayarlar > Uygulamalar > Galaxy Buds2 Manager > Yakındaki cihazlar iznini aç.");}}}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;}
    private Button b(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);return x;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private void add(LinearLayout r,Button x){r.addView(x,new LinearLayout.LayoutParams(0,-2,1));}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(8),dp(10),dp(8));root.addView(t("BUDS2 / SM-R177 — GALAXYBUDSCLIENT MODE",23));status=t("● Bluetooth izni kontrol ediliyor…",13);root.addView(status);
        LinearLayout pick=row();spinner=new Spinner(this);pick.addView(spinner,new LinearLayout.LayoutParams(0,-2,1));Button ref=b("YENİLE");ref.setOnClickListener(v->{if(btPermission())loadBonded();else requestBluetoothPermissionIfNeeded();});pick.addView(ref);root.addView(pick);
        LinearLayout con=row();Button connect=b("BAĞLAN"),disconnect=b("KES");connect.setOnClickListener(v->connect());disconnect.setOnClickListener(v->disconnect());add(con,connect);add(con,disconnect);root.addView(con);
        root.addView(t("NOISE CONTROL",18));LinearLayout nc=row();Button off=b("OFF"),anc=b("ANC"),amb=b("AMBIENT");off.setOnClickListener(v->send(NOISE_CONTROLS,new byte[]{0}));anc.setOnClickListener(v->send(NOISE_CONTROLS,new byte[]{1}));amb.setOnClickListener(v->send(NOISE_CONTROLS,new byte[]{2}));add(nc,off);add(nc,anc);add(nc,amb);root.addView(nc);
        LinearLayout nca=row();Button high=b("ANC HIGH"),low=b("ANC LOW"),one=b("1 EARBUD ANC");high.setOnClickListener(v->send(NOISE_LEVEL,new byte[]{1}));low.setOnClickListener(v->send(NOISE_LEVEL,new byte[]{0}));one.setOnClickListener(v->send(ANC_ONE,new byte[]{1}));add(nca,high);add(nca,low);add(nca,one);root.addView(nca);
        root.addView(t("EQUALIZER",18));LinearLayout eq=row();String[] names={"OFF","BASS","SOFT","DYNAMIC","CLEAR","TREBLE"};int[] vals={0,1,2,3,4,5};for(int i=0;i<names.length;i++){final int p=vals[i];Button x=b(names[i]);x.setOnClickListener(v->send(EQUALIZER,new byte[]{(byte)p}));add(eq,x);}root.addView(eq);
        root.addView(t("AMBIENT",18));LinearLayout av=row();Button aOn=b("AÇ"),aOff=b("KAPAT");aOn.setOnClickListener(v->send(AMBIENT_MODE,new byte[]{1}));aOff.setOnClickListener(v->send(AMBIENT_MODE,new byte[]{0}));add(av,aOn);add(av,aOff);root.addView(av);LinearLayout vol=row();for(int i=1;i<=5;i++){final int n=i;Button x=b("VOL "+i);x.setOnClickListener(v->send(AMBIENT_VOLUME,new byte[]{(byte)n}));add(vol,x);}root.addView(vol);
        root.addView(t("TOUCH",18));LinearLayout tl=row();Button lock=b("KİLİTLE"),unlock=b("AÇ");lock.setOnClickListener(v->send(LOCK_TOUCHPAD,new byte[]{1}));unlock.setOnClickListener(v->send(LOCK_TOUCHPAD,new byte[]{0}));add(tl,lock);add(tl,unlock);root.addView(tl);LinearLayout th=row();Button cycle=b("ANC↔AMBIENT"),amboff=b("AMBIENT↔OFF"),anoff=b("ANC↔OFF");cycle.setOnClickListener(v->send(TOUCH_HOLD_NOISE,new byte[]{1,1,0}));amboff.setOnClickListener(v->send(TOUCH_HOLD_NOISE,new byte[]{0,1,1}));anoff.setOnClickListener(v->send(TOUCH_HOLD_NOISE,new byte[]{1,0,1}));add(th,cycle);add(th,amboff);add(th,anoff);root.addView(th);
        root.addView(t("CONVERSATION DETECTION",18));LinearLayout cv=row();Button cvOn=b("AÇ"),cvOff=b("KAPAT");cvOn.setOnClickListener(v->send(DETECT_CONV,new byte[]{1}));cvOff.setOnClickListener(v->send(DETECT_CONV,new byte[]{0}));add(cv,cvOn);add(cv,cvOff);root.addView(cv);LinearLayout ct=row();String[] secs={"5 s","10 s","15 s"};for(int i=0;i<3;i++){final int n=i;Button x=b(secs[i]);x.setOnClickListener(v->send(DETECT_CONV_DURATION,new byte[]{(byte)n}));add(ct,x);}root.addView(ct);
        root.addView(t("SPATIAL AUDIO",18));LinearLayout sp=row();Button spOn=b("SPATIAL AÇ"),attach=b("ATTACH"),keep=b("KEEPALIVE"),detach=b("DETACH"),spOff=b("SPATIAL KAPAT");spOn.setOnClickListener(v->send(SPATIAL_AUDIO,new byte[]{1}));attach.setOnClickListener(v->send(SPATIAL_CONTROL,new byte[]{0}));keep.setOnClickListener(v->send(SPATIAL_CONTROL,new byte[]{4}));detach.setOnClickListener(v->send(SPATIAL_CONTROL,new byte[]{1}));spOff.setOnClickListener(v->send(SPATIAL_AUDIO,new byte[]{0}));add(sp,spOn);add(sp,attach);add(sp,keep);add(sp,detach);add(sp,spOff);root.addView(sp);
        root.addView(t("FIND MY EARBUDS",18));LinearLayout fm=row();Button fs=b("BAŞLAT"),fe=b("DURDUR");fs.setOnClickListener(v->send(FIND_START,new byte[0]));fe.setOnClickListener(v->send(FIND_STOP,new byte[0]));add(fm,fs);add(fm,fe);root.addView(fm);
        root.addView(t("DEVICE / DIAGNOSTICS",18));LinearLayout di=row();Button info=b("MANAGER INFO"),fit=b("FIT TEST"),self=b("SELF TEST");info.setOnClickListener(v->send(MANAGER_INFO,new byte[0]));fit.setOnClickListener(v->send(FIT,new byte[]{1}));self.setOnClickListener(v->send(SELF_TEST,new byte[0]));add(di,info);add(di,fit);add(di,self);root.addView(di);LinearLayout misc=row();Button time=b("SYNC TIME"),capture=b("CAPTURE MARK");time.setOnClickListener(v->send(UPDATE_TIME,timePayload()));capture.setOnClickListener(v->append("CAPTURE_MARK"));add(misc,time);add(misc,capture);root.addView(misc);
        root.addView(t("ADVANCED / FIRMWARE",18));root.addView(t("ACT/FOTA/debug/hidden commands catalogued but not blindly transmitted on R177.",12));Button catalog=b("PROTOCOL CATALOG");catalog.setOnClickListener(v->showCatalog());root.addView(catalog);logView=t("TX/RX log\n",11);logView.setTextIsSelectable(true);ScrollView sv=new ScrollView(this);sv.addView(logView);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void showCatalog(){String[] ids={"15 HOT_COMMAND_MANAGE","16 SET_MODE_CHANGE","17 GET_MODE","32 SET_DEBUG_MODE","65 METERING_REPORT","66 ACK","75 ACT_TEST_CMD","96 STATUS_UPDATED","97 EXTENDED_STATUS_UPDATED","98 CONNECTION_UPDATED","99 VERSION_INFO","111 SET_ANC_WITH_ONE_EARBUD","120 NOISE_CONTROLS","121 TOUCH_AND_HOLD_NOISE","122 SET_DETECT_CONVERSATIONS","123 CONVERSATION_DURATION","124 SET_SPATIAL_AUDIO","128 SET_AMBIENT_MODE","130 CUSTOMIZE_AMBIENT","131 NOISE_REDUCTION_LEVEL","132 AMBIENT_VOLUME","134 EQUALIZER","136 MANAGER_INFO","144 LOCK_TOUCHPAD","146 SET_TOUCHPAD_OPTION","147 SET_TOUCHPAD_OTHER_OPTION","151 SET_VOICE_WAKE_UP","157 FIT_TEST","158 FIT_RESULT","160 FIND_MY_START","161 FIND_MY_STOP","167 UPDATE_TIME","171 SELF_TEST","172 SET_FMM_CONFIG","173 GET_FMM_CONFIG","176-190 FOTA","194 SPATIAL_AUDIO_DATA","195 SPATIAL_AUDIO_CONTROL","197 ADAPTIVE_VOLUME","202 OVERHEAT","204 HEARING_TEST_DATA","217-219 ADAPTIVE_EQ","241 DEBUG_ERROR_CODE","242 DEBUG_EVENT","254 SD_TOUCH_RES","255 SET_TOUCH_TYPE"};ScrollView s=new ScrollView(this);TextView v=t("GALAXYBUDSCLIENT MESSAGE CATALOG\n\n"+String.join("\n",ids),13);s.addView(v);new AlertDialog.Builder(this).setTitle("Protocol Catalog").setView(s).setPositiveButton("KAPAT",null).show();}
    private void loadBonded(){if(adapter==null)return;if(!btPermission()){requestBluetoothPermissionIfNeeded();return;}try{devices.clear();ArrayList<String> names=new ArrayList<>();for(BluetoothDevice d:adapter.getBondedDevices()){devices.add(d);names.add(safeName(d)+"  "+d.getAddress());}spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));status.setText(names.isEmpty()?"● Eşleşmiş cihaz yok":"● "+names.size()+" eşleşmiş cihaz");if(names.isEmpty())toast("Buds2'yi Android Bluetooth ayarlarından eşleştir.");}catch(SecurityException e){status.setText("● Bluetooth izni reddedildi");toast("Yakındaki cihazlar iznini ver ve YENİLE'ye bas.");append("PERMISSION_ERROR "+e);}}
    private String safeName(BluetoothDevice d){try{return d.getName()==null?"Bluetooth device":d.getName();}catch(SecurityException e){return"Bluetooth device";}}
    private void connect(){if(!btPermission()){requestBluetoothPermissionIfNeeded();return;}if(devices.isEmpty()){loadBonded();return;}int pos=spinner.getSelectedItemPosition();if(pos<0||pos>=devices.size()){toast("Buds2 seç");return;}selected=devices.get(pos);io.execute(()->{try{if(!btPermission())throw new SecurityException("BLUETOOTH_CONNECT permission not granted");if(adapter!=null)adapter.cancelDiscovery();BluetoothSocket s=selected.createRfcommSocketToServiceRecord(SPP_NEW);s.connect();socket=s;out=s.getOutputStream();connected=true;runOnUiThread(()->status.setText("● SPP YENİ BAĞLI — "+safeName(selected)));append("CONNECTED "+selected.getAddress()+" UUID="+SPP_NEW);readLoop(s);}catch(SecurityException e){connected=false;runOnUiThread(()->status.setText("● Bağlantı hatası: Bluetooth izni"));append("SECURITY_EXCEPTION "+e);toast("Android Ayarları > Uygulamalar > Galaxy Buds2 Manager > İzinler > Yakındaki cihazlar = İzin ver.");}catch(Exception e){connected=false;runOnUiThread(()->status.setText("● Bağlantı hatası: "+e.getClass().getSimpleName()));append("CONNECT_ERROR "+e);}});}
    private void readLoop(BluetoothSocket s){try{InputStream in=s.getInputStream();byte[] buf=new byte[2048];while(connected){int n=in.read(buf);if(n<0)break;append("RX "+hex(Arrays.copyOf(buf,n)));}}catch(Exception e){append("READ_END "+e);}finally{connected=false;runOnUiThread(()->status.setText("● Bağlantı kesildi"));}}
    private void disconnect(){connected=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}append("DISCONNECTED");}
    private void send(int id,byte[] payload){if(!connected||out==null){toast("Önce Buds2'ye bağlan");return;}byte[] frame=frame(id,payload);io.execute(()->{try{out.write(frame);out.flush();append(String.format(Locale.US,"TX id=%d payload=%s FRAME=%s",id,hex(payload),hex(frame)));}catch(Exception e){append("TX_ERROR "+e);}});}
    private byte[] frame(int id,byte[] payload){int declared=1+payload.length+2;byte[] b=new byte[declared+4];b[0]=(byte)0xFD;b[1]=(byte)declared;b[2]=(byte)(declared>>8);b[3]=(byte)id;System.arraycopy(payload,0,b,4,payload.length);int crc=crc16(b,3,declared-2);int p=b.length-3;b[p]=(byte)crc;b[p+1]=(byte)(crc>>8);b[p+2]=(byte)0xDD;return b;}
    private int crc16(byte[] b,int off,int len){int c=0;for(int i=off;i<off+len;i++){c^=(b[i]&255)<<8;for(int j=0;j<8;j++)c=((c&0x8000)!=0)?((c<<1)^0x1021)&0xffff:(c<<1)&0xffff;}return c;}
    private byte[] timePayload(){long now=System.currentTimeMillis();int tz=TimeZone.getDefault().getOffset(now);return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(now).putInt(tz).array();}
    private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b){if(s.length()>0)s.append(' ');s.append(String.format(Locale.US,"%02X",x&255));}return s.toString();}
    private void append(String s){runOnUiThread(()->{log.append(new java.text.SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())).append(" ").append(s).append("\n");if(log.length()>12000)log.delete(0,3000);logView.setText(log.toString());});}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){disconnect();io.shutdownNow();super.onDestroy();}
}
