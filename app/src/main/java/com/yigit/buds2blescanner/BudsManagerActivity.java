package com.yigit.buds2blescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GalaxyBudsClient-style Android manager for Galaxy Buds2.
 *
 * Architecture intentionally follows the public GalaxyBudsClient protocol model:
 * Bluetooth device -> RFCOMM/SPP -> framed message -> message dispatcher -> state model.
 * The Buds2 captures from this project use FD/DD framing, so the existing
 * BudsProtocolAnalyzer remains the wire decoder.
 *
 * Control TX is conservative: status responses and time synchronization are enabled;
 * destructive/debug/FOTA operations are not exposed.
 */
public class BudsManagerActivity extends Activity {
    private static final int REQ = 7001;
    private static final int SAVE = 7002;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID ANDROID_BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter adapter;
    private BluetoothLeScanner leScanner;
    private BluetoothSocketHolder socketHolder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final BudsProtocolAnalyzer analyzer = new BudsProtocolAnalyzer();
    private final LinkedHashMap<String,BluetoothDevice> bleDevices = new LinkedHashMap<>();
    private BluetoothDevice selectedClassic;
    private boolean scanning;
    private boolean connected;
    private boolean autoRespond = true;
    private boolean timeSync = true;
    private final StringBuilder capture = new StringBuilder();
    private boolean firstCapture = true;

    private TextView connection, deviceTitle, left, right, caseBattery, firmware, ambient, eq, wearing, rawStatus, addressInfo;
    private LinearLayout bleList, classicList;
    private Button scanButton, connectButton, disconnectButton, saveButton, refreshClassic;

    private int batteryL=-1, batteryR=-1, wearingState=-1, ambientState=-1, eqState=-1;
    private String model="SM-R177 / Galaxy Buds2";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=bm.getAdapter();
        buildUi();
        requestBtPermissions();
        refreshBonded();
    }

    private TextView text(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(dp(4),dp(4),dp(4),dp(4));return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(8),dp(10),dp(8));
        TextView title=text("Galaxy Buds2 Manager",24);root.addView(title);
        connection=text("● Hazır — GalaxyBudsClient tarzı SPP/RFCOMM manager",14);root.addView(connection);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        scanButton=btn("BLE TARA");connectButton=btn("BAĞLAN");disconnectButton=btn("KES");saveButton=btn("CAPTURE KAYDET");
        actions.addView(scanButton,weight());actions.addView(connectButton,weight());actions.addView(disconnectButton,weight());actions.addView(saveButton,weight());root.addView(actions);
        scanButton.setOnClickListener(v->scanBle());connectButton.setOnClickListener(v->connect());disconnectButton.setOnClickListener(v->disconnect());saveButton.setOnClickListener(v->saveCapture());
        disconnectButton.setEnabled(false);saveButton.setEnabled(false);connectButton.setEnabled(false);

        deviceTitle=text("Cihaz seçilmedi",15);root.addView(deviceTitle);
        addressInfo=text("BLE adresi ile Classic/SPP adresi ayrı olabilir. Bağlantı için eşleşmiş Classic cihaz seçilir.",11);root.addView(addressInfo);

        LinearLayout statusPanel=new LinearLayout(this);statusPanel.setOrientation(LinearLayout.VERTICAL);
        left=text("Sol: —",15);right=text("Sağ: —",15);caseBattery=text("Kutu: —",15);firmware=text("Firmware: —",12);ambient=text("ANC/Ambient: —",13);eq=text("EQ: —",13);wearing=text("Takılılık: —",13);
        statusPanel.addView(left);statusPanel.addView(right);statusPanel.addView(caseBattery);statusPanel.addView(firmware);statusPanel.addView(ambient);statusPanel.addView(eq);statusPanel.addView(wearing);
        root.addView(statusPanel);

        root.addView(text("BLE cihazları",13));ScrollView bs=new ScrollView(this);bleList=new LinearLayout(this);bleList.setOrientation(LinearLayout.VERTICAL);bs.addView(bleList);root.addView(bs,new LinearLayout.LayoutParams(-1,dp(120)));
        LinearLayout ch=new LinearLayout(this);ch.addView(text("Eşleşmiş Classic / SPP cihazları",13),new LinearLayout.LayoutParams(0,-2,1));refreshClassic=btn("YENİLE");refreshClassic.setOnClickListener(v->refreshBonded());ch.addView(refreshClassic);root.addView(ch);
        ScrollView cs=new ScrollView(this);classicList=new LinearLayout(this);classicList.setOrientation(LinearLayout.VERTICAL);cs.addView(classicList);root.addView(cs,new LinearLayout.LayoutParams(-1,dp(120)));

        rawStatus=text("Protocol state: idle",11);root.addView(rawStatus);
        ScrollView logScroll=new ScrollView(this);rawStatus.setTextIsSelectable(true);logScroll.addView(rawStatus);root.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}

    private void requestBtPermissions(){
        if(android.os.Build.VERSION.SDK_INT>=31){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ);
        } else if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);
    }
    @SuppressLint("MissingPermission") private boolean ready(){
        if(adapter==null){toast("Bluetooth desteklenmiyor");return false;}
        if(!adapter.isEnabled()){toast("Bluetooth kapalı");startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return false;}
        return android.os.Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @SuppressLint("MissingPermission") private void refreshBonded(){
        if(!ready())return; classicList.removeAllViews();
        Set<BluetoothDevice> set=adapter.getBondedDevices();
        for(BluetoothDevice d:set){
            TextView row=text((safeName(d))+"\nClassic: "+d.getAddress()+"\nBond: "+bond(d),13);
            row.setOnClickListener(v->{selectedClassic=d;connectButton.setEnabled(true);deviceTitle.setText("SEÇİLİ: "+safeName(d));addressInfo.setText("Classic/SPP: "+d.getAddress()+"\nBLE adresi farklı olabilir; bu normaldir.");});
            classicList.addView(row);
        }
        if(set.isEmpty())classicList.addView(text("Eşleşmiş cihaz yok. Önce Android Bluetooth ayarlarından Buds2'yi eşleştir.",12));
    }

    @SuppressLint("MissingPermission") private void scanBle(){
        if(!ready())return;
        if(scanning){stopBle();return;}
        bleDevices.clear();bleList.removeAllViews();leScanner=adapter.getBluetoothLeScanner();
        if(leScanner==null){toast("BLE scanner yok");return;}
        scanning=true;scanButton.setText("DURDUR");connection.setText("● BLE taranıyor");
        leScanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
        main.postDelayed(this::stopBle,10000);
    }
    @SuppressLint("MissingPermission") private void stopBle(){if(!scanning)return;scanning=false;if(leScanner!=null)leScanner.stopScan(scanCallback);scanButton.setText("BLE TARA");}
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){BluetoothDevice d=r.getDevice();String a=d.getAddress();if(!bleDevices.containsKey(a)){bleDevices.put(a,d);addBle(d,r.getRssi());}}
        @Override public void onScanFailed(int e){main.post(()->connection.setText("● BLE scan error "+e));}
    };
    @SuppressLint("MissingPermission") private void addBle(BluetoothDevice d,int rssi){
        TextView row=text(safeName(d)+"\nBLE: "+d.getAddress()+" | RSSI "+rssi+" dBm",13);
        row.setOnClickListener(v->{deviceTitle.setText("BLE: "+safeName(d));addressInfo.setText("BLE: "+d.getAddress()+"\nClassic/SPP bağlantısı için eşleşmiş cihaz listesinden aynı Buds2'yi seç.");autoSelectBondedByName(safeName(d));});
        bleList.addView(row);
    }
    @SuppressLint("MissingPermission") private void autoSelectBondedByName(String name){
        if(name==null)return;for(BluetoothDevice d:adapter.getBondedDevices())if(name.equalsIgnoreCase(safeName(d)) || name.toLowerCase(Locale.US).contains("buds2") && safeName(d).toLowerCase(Locale.US).contains("buds2")){selectedClassic=d;connectButton.setEnabled(true);deviceTitle.setText("SEÇİLİ: "+safeName(d));addressInfo.setText("BLE cihazı ve Classic cihazı isimle eşleştirildi.\nBLE: taramadaki adres\nClassic/SPP: "+d.getAddress());return;}
    }

    @SuppressLint("MissingPermission") private void connect(){
        if(selectedClassic==null){toast("Önce eşleşmiş Classic/SPP cihaz seç");return;} if(connected){return;}
        analyzer.reset();capture.setLength(0);firstCapture=true;log("CONNECT_START model=SM-R177;classic="+selectedClassic.getAddress()+";uuid="+SPP);
        connectButton.setEnabled(false);disconnectButton.setEnabled(true);saveButton.setEnabled(true);connection.setText("● RFCOMM bağlanıyor...");
        io.execute(()->{
            try{
                BluetoothSocket s;
                try{s=selectedClassic.createRfcommSocketToServiceRecord(SPP);}catch(Exception e){s=selectedClassic.createInsecureRfcommSocketToServiceRecord(SPP);}
                adapter.cancelDiscovery();s.connect();socketHolder=new BluetoothSocketHolder(s);connected=true;
                main.post(()->{connection.setText("● RFCOMM / SPP BAĞLI");log("RFCOMM_CONNECTED address="+selectedClassic.getAddress());});
                readLoop(s);
            }catch(Exception e){connected=false;main.post(()->{connection.setText("● RFCOMM başarısız: "+e.getClass().getSimpleName());log("CONNECT_ERROR "+e);connectButton.setEnabled(true);disconnectButton.setEnabled(false);});}
        });
    }

    private void readLoop(BluetoothSocket s){
        try{byte[] buf=new byte[1024];InputStream in=s.getInputStream();while(connected){int n=in.read(buf);if(n<0)break;byte[] chunk=Arrays.copyOf(buf,n);List<BudsProtocolAnalyzer.Frame> frames=analyzer.feed(chunk);for(BudsProtocolAnalyzer.Frame f:frames)handleFrame(f);log("RX "+hex(chunk));}}catch(Exception e){main.post(()->log("RFCOMM_READ_END "+e));}finally{connected=false;main.post(()->{connection.setText("● Bağlantı kesildi");disconnectButton.setEnabled(false);connectButton.setEnabled(true);});}}

    private void handleFrame(BudsProtocolAnalyzer.Frame f){
        main.post(()->{log(f.summary());applyState(f);});
        // GalaxyBudsClient responds to status messages. Keep this conservative and
        // only answer status/version frames; no control or firmware packets are emitted.
        if(autoRespond && (f.messageId==0x60 || f.messageId==0x61 || f.messageId==0x62 || f.messageId==0x63)){
            byte[] response=makeResponse(f.bytes);
            io.execute(()->send(response));
            if(timeSync && f.messageId==0x61){main.postDelayed(()->io.execute(()->send(makeUpdateTime())),120);}
        }
    }

    private void applyState(BudsProtocolAnalyzer.Frame f){
        if(f.messageId==0x60 && f.payloadLength>=6){byte[] b=f.bytes;int p=4;batteryL=u8(b,p+1);batteryR=u8(b,p+2);wearingState=u8(b,p+5);}
        if(f.messageId==0x61 && f.payloadLength>=12){byte[] b=f.bytes;int p=4;batteryL=u8(b,p+2);batteryR=u8(b,p+3);wearingState=u8(b,p+6);ambientState=u8(b,p+7);eqState=u8(b,p+10);}
        if(f.messageId==0x63)firmware.setText("Firmware/raw: "+hexRange(f.bytes,4,f.bytes.length-3));
        left.setText("Sol: "+pct(batteryL));right.setText("Sağ: "+pct(batteryR));caseBattery.setText("Kutu: Buds2 SPP durum mesajında yok / ayrı family");
        ambient.setText("ANC/Ambient: "+ambientName(ambientState));eq.setText("EQ: "+eqName(eqState));wearing.setText("Takılılık: "+wearingName(wearingState));
    }
    private String pct(int x){return x<0?"—":x+"%";}
    private String ambientName(int x){if(x<0)return "—";return x==1?"Açık":"Kapalı";}
    private String eqName(int x){if(x<0)return "—";String[] n={"Custom/Disabled","Bass boost","Soft","Dynamic","Clear","Treble"};return x<n.length?n[x]:"Mode "+x;}
    private String wearingName(int x){switch(x){case 0:return "Yok";case 1:return "Sol";case 16:return "Sağ";case 17:return "İkisi";default:return "0x"+Integer.toHexString(x);}}

    private byte[] makeResponse(byte[] request){
        if(request==null||request.length<8)return new byte[0];
        byte[] out=Arrays.copyOf(request,request.length);int h=(out[1]&255)|((out[2]&255)<<8);h|=0x1000;out[1]=(byte)h;out[2]=(byte)(h>>8);
        int crcPos=out.length-3;int crc=crc16(out,3,crcPos-3);out[crcPos]=(byte)crc;out[crcPos+1]=(byte)(crc>>8);out[out.length-1]=(byte)0xDD;return out;
    }
    private byte[] makeUpdateTime(){
        byte[] payload=new byte[12];long ms=System.currentTimeMillis();long tz=TimeZone.getDefault().getOffset(ms);ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putLong(ms).putInt((int)tz);return makeFrame(0xA7,payload);
    }
    private byte[] makeFrame(int id,byte[] payload){
        int declared=1+payload.length+2,total=declared+4;byte[] b=new byte[total];b[0]=(byte)0xFD;b[1]=(byte)declared;b[2]=(byte)(declared>>8);b[3]=(byte)id;System.arraycopy(payload,0,b,4,payload.length);int crc=crc16(b,3,payload.length+1);int p=total-3;b[p]=(byte)crc;b[p+1]=(byte)(crc>>8);b[p+2]=(byte)0xDD;return b;
    }
    private void send(byte[] b){if(!connected||socketHolder==null||b==null||b.length==0)return;try{OutputStream o=socketHolder.out();o.write(b);o.flush();main.post(()->log("TX "+hex(b)));}catch(Exception e){main.post(()->log("TX_ERROR "+e));}}

    private int crc16(byte[] b,int off,int len){int crc=0;for(int i=off;i<off+len;i++){crc^=(b[i]&255)<<8;for(int j=0;j<8;j++)crc=((crc&0x8000)!=0)?((crc<<1)^0x1021)&0xffff:(crc<<1)&0xffff;}return crc&0xffff;}
    private int u8(byte[] b,int i){return b[i]&255;}
    private String hex(byte[] b){if(b==null)return "";StringBuilder s=new StringBuilder();for(byte x:b){if(s.length()>0)s.append(' ');s.append(String.format(Locale.US,"%02X",x&255));}return s.toString();}
    private String hexRange(byte[] b,int a,int z){return hex(Arrays.copyOfRange(b,a,z));}
    @SuppressLint("MissingPermission")private String safeName(BluetoothDevice d){try{String n=d.getName();return n==null?"Unknown":n;}catch(Exception e){return "Unknown";}}
    @SuppressLint("MissingPermission")private String bond(BluetoothDevice d){try{int x=d.getBondState();return x==BluetoothDevice.BOND_BONDED?"BONDED":x==BluetoothDevice.BOND_BONDING?"BONDING":"NONE";}catch(Exception e){return "?";}}
    private void log(String s){String line=new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+"  "+s;captureEvent("LOG",s);main.post(()->{rawStatus.append("\n"+line);});}
    private void captureEvent(String type,String data){synchronized(capture){if(firstCapture){capture.append("[\n");firstCapture=false;}else capture.append(",\n");capture.append("{\"time\":\"").append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date())).append("\",\"type\":\"").append(type).append("\",\"data\":\"").append(escape(data)).append("\"}");}}
    private String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    private void saveCapture(){if(firstCapture){toast("Önce bağlantı/capture oluştur");return;}capture.append("\n]\n");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"buds2_manager_capture_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".json");startActivityForResult(i,SAVE);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==SAVE&&c==RESULT_OK&&d!=null&&d.getData()!=null){try(OutputStream o=getContentResolver().openOutputStream(d.getData())){o.write(capture.toString().getBytes("UTF-8"));toast("Capture kaydedildi");}catch(Exception e){toast("Kaydetme hatası: "+e.getMessage());}}}
    @SuppressLint("MissingPermission")private void disconnect(){connected=false;try{if(socketHolder!=null)socketHolder.close();}catch(Exception ignored){}socketHolder=null;connection.setText("● Kesildi");disconnectButton.setEnabled(false);connectButton.setEnabled(selectedClassic!=null);}
    @Override protected void onDestroy(){connected=false;try{if(socketHolder!=null)socketHolder.close();}catch(Exception ignored){}io.shutdownNow();super.onDestroy();}

    private static final class BluetoothSocketHolder{final android.bluetooth.BluetoothSocket socket;BluetoothSocketHolder(android.bluetooth.BluetoothSocket s){socket=s;}InputStream in()throws Exception{return socket.getInputStream();}OutputStream out()throws Exception{return socket.getOutputStream();}void close(){try{socket.close();}catch(Exception ignored){}}}
}
