package com.yigit.buds2blescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQUEST_BT = 1001;
    private static final int CREATE_FILE = 2001;
    private static final long SCAN_TIME_MS = 12000L;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, log;
    private Button scanButton, exportButton;
    private final StringBuilder capture = new StringBuilder();
    private final Set<String> notified = new HashSet<>();
    private final List<BluetoothGattCharacteristic> readable = new ArrayList<>();
    private int readIndex = 0;
    private String deviceName = "unknown";
    private String deviceAddress = "unknown";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        buildUi();
        requestPermissionsIfNeeded();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p=dp(12); root.setPadding(p,p,p,p);
        status=new TextView(this); status.setText("Hazır"); status.setTextSize(16); root.addView(status);
        scanButton=new Button(this); scanButton.setText("BLE TARA"); scanButton.setOnClickListener(v->startScan()); root.addView(scanButton);
        exportButton=new Button(this); exportButton.setText("CAPTURE KAYDET"); exportButton.setEnabled(false); exportButton.setOnClickListener(v->saveCapture()); root.addView(exportButton);
        ScrollView scroll=new ScrollView(this); log=new TextView(this); log.setTextSize(12); log.setTextIsSelectable(true); scroll.addView(log);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    private void requestPermissionsIfNeeded(){
        if(Build.VERSION.SDK_INT>=31){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQUEST_BT);
        }else if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_BT);
    }

    @SuppressLint("MissingPermission") private boolean ready(){
        if(adapter==null){append("Bluetooth desteklenmiyor.");return false;}
        if(!adapter.isEnabled()){append("Bluetooth kapalı.");return false;}
        return Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
    }

    private void newCapture(){
        capture.setLength(0); notified.clear(); readable.clear(); readIndex=0;
        capture.append("{\n  \"capture_version\": 1,\n");
        capture.append("  \"started\": \"").append(ts()).append("\",\n");
        capture.append("  \"events\": [\n");
    }
    private boolean firstEvent=true;
    private void event(String type,String data){
        synchronized(capture){
            if(!firstEvent) capture.append(",\n"); firstEvent=false;
            capture.append("    {\"time\":\"").append(ts()).append("\",\"type\":\"").append(esc(type)).append("\",\"data\":\"").append(esc(data)).append("\"}");
        }
    }
    private String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    private String ts(){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date());}

    @SuppressLint("MissingPermission") private void startScan(){
        if(!ready())return;
        scanner=adapter.getBluetoothLeScanner(); if(scanner==null){append("BLE scanner alınamadı.");return;}
        newCapture(); append("=== BLE SCAN ==="); event("SCAN_START","BLE scan started");
        status.setText("Tarama yapılıyor..."); scanButton.setEnabled(false);
        scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
        handler.postDelayed(this::stopScan,SCAN_TIME_MS);
    }
    @SuppressLint("MissingPermission") private void stopScan(){
        if(scanner!=null)scanner.stopScan(callback); scanButton.setEnabled(true); status.setText("Tarama tamamlandı"); event("SCAN_END","BLE scan ended");
    }
    private final ScanCallback callback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){
            BluetoothDevice d=r.getDevice(); String name=""; try{name=d.getName();}catch(Exception ignored){}
            if(name==null||name.isEmpty())name="(isimsiz)";
            String addr=safeAddress(d); String adv=scanRecordHex(r.getScanRecord());
            String line=name+" | "+addr+" | RSSI "+r.getRssi(); append(line);
            event("ADVERTISEMENT", "name="+name+";address="+addr+";rssi="+r.getRssi()+";txPower="+r.getTxPower()+";raw="+adv);
            if(name.toLowerCase(Locale.ROOT).contains("buds")) connect(d);
        }
        @Override public void onScanFailed(int e){append("Scan error: "+e);event("SCAN_ERROR",String.valueOf(e));scanButton.setEnabled(true);}
    };
    @SuppressLint("MissingPermission") private String scanRecordHex(ScanRecord r){return r==null?"null":hex(r.getBytes());}
    @SuppressLint("MissingPermission") private void connect(BluetoothDevice d){
        stopScan(); if(gatt!=null){gatt.close();gatt=null;} deviceName=safeName(d);deviceAddress=safeAddress(d);
        event("CONNECT_START",deviceName+"|"+deviceAddress); status.setText("Bağlanıyor..."); append("=== GATT CONNECT ===");
        gatt=d.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int statusCode,int state){
            if(state==BluetoothGatt.STATE_CONNECTED){status.setText("Bağlandı — otomatik capture");append("Bağlandı; servisler keşfediliyor...");event("CONNECTED","status="+statusCode);g.discoverServices();}
            else if(state==BluetoothGatt.STATE_DISCONNECTED){append("Bağlantı kesildi: "+statusCode);event("DISCONNECTED","status="+statusCode);}
        }
        @SuppressLint("MissingPermission") @Override public void onServicesDiscovered(BluetoothGatt g,int statusCode){
            if(statusCode!=BluetoothGatt.GATT_SUCCESS){append("Service discovery başarısız: "+statusCode);event("SERVICE_DISCOVERY_ERROR",String.valueOf(statusCode));return;}
            append("=== ALL GATT SERVICES ==="); event("GATT_DISCOVERY","services="+g.getServices().size()); readable.clear();
            for(BluetoothGattService s:g.getServices()){
                append("SERVICE "+s.getUuid()+" type="+s.getType()); event("SERVICE",s.getUuid().toString()+" type="+s.getType());
                for(BluetoothGattCharacteristic c:s.getCharacteristics()){
                    append(" CHAR "+c.getUuid()+" properties="+properties(c.getProperties()));
                    event("CHARACTERISTIC",c.getUuid()+" properties="+properties(c.getProperties()));
                    for(BluetoothGattDescriptor d:c.getDescriptors()){append("  DESC "+d.getUuid());event("DESCRIPTOR",d.getUuid().toString());}
                    if((c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)readable.add(c);
                    if((c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0 || (c.getProperties()&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0) enableNotify(c);
                }
            }
            exportButton.setEnabled(true); readNext();
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int statusCode){
            String h=hex(value);append("READ "+c.getUuid()+" -> "+h+" status="+statusCode);event("READ","uuid="+c.getUuid()+";status="+statusCode+";hex="+h);readIndex++;handler.postDelayed(MainActivity.this::readNext,80);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){
            String h=hex(value);append("NOTIFY "+c.getUuid()+" -> "+h);event("NOTIFY","uuid="+c.getUuid()+";hex="+h);
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int statusCode){event("DESCRIPTOR_WRITE","uuid="+d.getUuid()+";status="+statusCode);}
    };

    @SuppressLint("MissingPermission") private void readNext(){if(gatt!=null&&readIndex<readable.size())gatt.readCharacteristic(readable.get(readIndex));else {event("CAPTURE_READY","automatic capture complete");status.setText("Capture tamamlandı — kaydedilebilir");append("=== CAPTURE TAMAMLANDI ===");exportButton.setEnabled(true);}}
    @SuppressLint("MissingPermission") private void enableNotify(BluetoothGattCharacteristic c){
        String key=c.getUuid().toString(); if(notified.contains(key)||gatt==null)return; notified.add(key);
        boolean ok=gatt.setCharacteristicNotification(c,true); event("NOTIFY_ENABLE",c.getUuid().toString()+";local="+ok);
        BluetoothGattDescriptor d=c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if(d!=null){d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);gatt.writeDescriptor(d);}
    }

    private void saveCapture(){
        synchronized(capture){
            String body=capture.toString()+"\n  ],\n  \"device\": {\"name\":\""+esc(deviceName)+"\",\"address\":\""+esc(deviceAddress)+"\"},\n  \"finished\": \""+ts()+"\"\n}\n";
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"buds2_capture_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".json");pendingExport=body;startActivityForResult(i,CREATE_FILE);
        }
    }
    private String pendingExport="";
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==CREATE_FILE&&result==RESULT_OK&&data!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){o.write(pendingExport.getBytes("UTF-8"));append("CAPTURE KAYDEDİLDİ: "+data.getData());status.setText("Capture kaydedildi");}catch(Exception e){append("Kayıt hatası: "+e);}}}

    private String properties(int p){StringBuilder s=new StringBuilder();if((p&BluetoothGattCharacteristic.PROPERTY_READ)!=0)s.append("READ ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)s.append("WRITE ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)s.append("WRITE_NR ");if((p&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)s.append("NOTIFY ");if((p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)s.append("INDICATE ");if((p&BluetoothGattCharacteristic.PROPERTY_BROADCAST)!=0)s.append("BROADCAST ");return s.toString().trim();}
    private String hex(byte[] v){if(v==null)return"null";StringBuilder s=new StringBuilder();for(byte b:v)s.append(String.format(Locale.US,"%02X ",b&255));return s.toString().trim();}
    @SuppressLint("MissingPermission") private String safeName(BluetoothDevice d){try{return d.getName()==null?"unknown":d.getName();}catch(Exception e){return"unknown";}}
    @SuppressLint("MissingPermission") private String safeAddress(BluetoothDevice d){try{return d.getAddress();}catch(Exception e){return"unknown";}}
    private void append(String t){runOnUiThread(()->log.append(t+"\n"));}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onDestroy(){if(scanner!=null)try{scanner.stopScan(callback);}catch(Exception ignored){}if(gatt!=null){try{gatt.disconnect();}catch(Exception ignored){}gatt.close();}super.onDestroy();}
}
