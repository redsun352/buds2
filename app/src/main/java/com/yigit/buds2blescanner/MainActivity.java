package com.yigit.buds2blescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ=1001, CREATE_FILE=2001;
    private static final long SCAN_MS=15000;
    private BluetoothAdapter adapter; private BluetoothLeScanner scanner; private BluetoothGatt gatt;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout deviceList, logPanel; private TextView status, count, selectedInfo, log;
    private Button scan, connect, disconnect, save, clear;
    private BluetoothDevice selected; private String selectedName="unknown", selectedAddress="unknown";
    private final LinkedHashMap<String,DeviceRow> devices=new LinkedHashMap<>();
    private final Set<String> notifySet=new HashSet<>(); private final ArrayList<BluetoothGattCharacteristic> readQueue=new ArrayList<>();
    private int readIndex; private boolean scanning; private String pendingExport="";
    private final StringBuilder capture=new StringBuilder(); private boolean firstEvent=true;

    static class DeviceRow { BluetoothDevice device; TextView card; int rssi; ScanRecord record; DeviceRow(BluetoothDevice d){device=d;} }

    @Override public void onCreate(Bundle b){super.onCreate(b); BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE); adapter=bm.getAdapter(); buildUi(); requestBtPermissions();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView title=label("Galaxy Buds2 Analyzer",24); root.addView(title);
        status=label("● Hazır",15); root.addView(status);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        scan=button("TARA"); connect=button("BAĞLAN"); disconnect=button("KES"); save=button("CAPTURE");
        connect.setEnabled(false); disconnect.setEnabled(false); save.setEnabled(false);
        scan.setOnClickListener(v->toggleScan()); connect.setOnClickListener(v->{if(selected!=null)connectDevice(selected);}); disconnect.setOnClickListener(v->disconnect()); save.setOnClickListener(v->saveCapture());
        actions.addView(scan,weight()); actions.addView(connect,weight()); actions.addView(disconnect,weight()); actions.addView(save,weight()); root.addView(actions);

        LinearLayout selectedBar=new LinearLayout(this); selectedBar.setOrientation(LinearLayout.VERTICAL); selectedBar.setPadding(dp(8),dp(5),dp(8),dp(5));
        selectedInfo=label("Cihaz seçilmedi — listeden bir cihaza dokun",13); count=label("0 cihaz",12); selectedBar.addView(selectedInfo); selectedBar.addView(count); root.addView(selectedBar);
        
        ScrollView ds=new ScrollView(this); deviceList=new LinearLayout(this); deviceList.setOrientation(LinearLayout.VERTICAL); ds.addView(deviceList); root.addView(ds,new LinearLayout.LayoutParams(-1,dp(300)));
        
        LinearLayout logHead=new LinearLayout(this); logHead.setOrientation(LinearLayout.HORIZONTAL); TextView lh=label("CANLI ANALİZ / GATT / HEX",13); clear=button("TEMİZLE"); clear.setOnClickListener(v->log.setText("")); logHead.addView(lh,new LinearLayout.LayoutParams(0,-2,1)); logHead.addView(clear); root.addView(logHead);
        ScrollView ls=new ScrollView(this); log=label("",11); log.setTextIsSelectable(true); ls.addView(log); root.addView(ls,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }

    private TextView label(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setPadding(0,dp(3),0,dp(3));return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}

    private void requestBtPermissions(){if(Build.VERSION.SDK_INT>=31){if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ);}else if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);}
    @SuppressLint("MissingPermission") private boolean ready(){if(adapter==null){msg("Bluetooth desteklenmiyor");return false;}if(!adapter.isEnabled()){msg("Bluetooth kapalı — lütfen açın");return false;}return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);}

    @SuppressLint("MissingPermission") private void toggleScan(){if(scanning){stopScan();return;}if(!ready())return;devices.clear();deviceList.removeAllViews();selected=null;connect.setEnabled(false);scanner=adapter.getBluetoothLeScanner();if(scanner==null){msg("BLE Scanner kullanılamıyor");return;}newCapture();scanning=true;scan.setText("DURDUR");status.setText("● BLE taraması aktif");event("SCAN_START","mode=LOW_LATENCY");scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),scanCallback);handler.postDelayed(this::stopScan,SCAN_MS);}
    @SuppressLint("MissingPermission") private void stopScan(){if(!scanning)return;scanning=false;if(scanner!=null)scanner.stopScan(scanCallback);scan.setText("TARA");if(gatt==null)status.setText("● Tarama tamamlandı");event("SCAN_END","devices="+devices.size());}

    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){BluetoothDevice d=r.getDevice();String a=safeAddress(d);DeviceRow row=devices.get(a);if(row==null){row=new DeviceRow(d);devices.put(a,row);addCard(row);}row.rssi=r.getRssi();row.record=r.getScanRecord();updateCard(row);event("ADVERTISEMENT","name="+safeName(d)+";address="+a+";rssi="+r.getRssi()+";tx="+r.getTxPower()+";raw="+hex(r.getScanRecord()==null?null:r.getScanRecord().getBytes()));}
        @Override public void onScanFailed(int e){event("SCAN_ERROR","code="+e);runOnUiThread(()->{status.setText("● Tarama hatası: "+e);scan.setText("TARA");scanning=false;});}
    };

    @SuppressLint("MissingPermission") private void addCard(DeviceRow row){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(7),dp(10),dp(7));card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        TextView top=label("",16); TextView sub=label("",12); TextView extra=label("",11); card.addView(top);card.addView(sub);card.addView(extra);
        card.setOnClickListener(v->{selectDevice(row);}); card.setOnLongClickListener(v->{showDeviceDetails(row);return true;}); row.card=top; card.setTag(row); deviceList.addView(card,new LinearLayout.LayoutParams(-1,dp(82))); updateCard(row);
    }
    @SuppressLint("MissingPermission") private void updateCard(DeviceRow row){if(row.card==null)return;ViewGroup card=(ViewGroup)row.card.getParent();String n=safeName(row.device);row.card.setText((selected==row.device?"✓ ":"")+n);((TextView)card.getChildAt(1)).setText(safeAddress(row.device)+"   RSSI "+row.rssi+" dBm");String data=row.record==null?"":("TX "+row.record.getTxPowerLevel()+" dBm   "+(row.record.getServiceUuids()==null?"0 services":row.record.getServiceUuids().size()+" services"));((TextView)card.getChildAt(2)).setText(data+"   • Dokun: seç | Uzun bas: detay");count.setText(devices.size()+" cihaz");}
    @SuppressLint("MissingPermission") private void selectDevice(DeviceRow row){selected=row.device;selectedName=safeName(selected);selectedAddress=safeAddress(selected);connect.setEnabled(gatt==null);selectedInfo.setText("Seçili: "+selectedName+"  |  "+selectedAddress);for(DeviceRow x:devices.values())updateCard(x);status.setText("● Cihaz seçildi — BAĞLAN'a bas");event("DEVICE_SELECTED",selectedName+"|"+selectedAddress);}
    @SuppressLint("MissingPermission") private void showDeviceDetails(DeviceRow row){StringBuilder s=new StringBuilder();s.append("Name: ").append(safeName(row.device)).append("\nAddress: ").append(safeAddress(row.device)).append("\nRSSI: ").append(row.rssi).append(" dBm\n");if(row.record!=null){s.append("TX Power: ").append(row.record.getTxPowerLevel()).append(" dBm\n");s.append("Services: ").append(row.record.getServiceUuids()).append("\n");s.append("Raw advertisement:\n").append(hex(row.record.getBytes()));}new AlertDialog.Builder(this).setTitle("Bluetooth cihazı").setMessage(s.toString()).setPositiveButton("SEÇ",(d,w)->selectDevice(row)).setNeutralButton("KAPAT",null).show();}

    @SuppressLint("MissingPermission") private void connectDevice(BluetoothDevice d){stopScan();closeGatt();selected=d;selectedName=safeName(d);selectedAddress=safeAddress(d);newCapture();event("CONNECT_START",selectedName+"|"+selectedAddress);status.setText("● Bağlanıyor…");connect.setEnabled(false);disconnect.setEnabled(true);save.setEnabled(true);append("=== GATT CONNECT ===");append("Device: "+selectedName+" | "+selectedAddress);gatt=d.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);}
    @SuppressLint("MissingPermission") private void closeGatt(){if(gatt!=null){try{gatt.disconnect();}catch(Exception ignored){}try{gatt.close();}catch(Exception ignored){}gatt=null;}}
    @SuppressLint("MissingPermission") private void disconnect(){if(gatt!=null){event("DISCONNECT_REQUEST","manual");try{gatt.disconnect();}catch(Exception ignored){}}}

    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int st,int state){event("GATT_STATE","state="+state+";status="+st+" ("+gattStatus(st)+")");if(state==BluetoothGatt.STATE_CONNECTED){runOnUiThread(()->{status.setText("● BAĞLI — MTU / servis keşfi");append("CONNECTED; status="+st+" ("+gattStatus(st)+")");});if(Build.VERSION.SDK_INT>=21)g.requestMtu(517);else g.discoverServices();}else if(state==BluetoothGatt.STATE_DISCONNECTED){runOnUiThread(()->{status.setText("● Bağlantı kesildi — status "+st+" / "+gattStatus(st));connect.setEnabled(selected!=null);disconnect.setEnabled(false);});}}
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int st){event("MTU","mtu="+mtu+";status="+st);append("MTU result: "+mtu+" / "+st);g.discoverServices();}
        @Override public void onServicesDiscovered(BluetoothGatt g,int st){if(st!=BluetoothGatt.GATT_SUCCESS){event("SERVICE_DISCOVERY_ERROR","status="+st+";"+gattStatus(st));append("SERVICE DISCOVERY ERROR: "+st+" "+gattStatus(st));return;}event("GATT_DISCOVERY","services="+g.getServices().size());readQueue.clear();readIndex=0;append("=== SERVICES "+g.getServices().size()+" ===");for(BluetoothGattService s:g.getServices()){event("SERVICE",s.getUuid()+";type="+s.getType());append("SERVICE  "+s.getUuid());for(BluetoothGattCharacteristic c:s.getCharacteristics()){String p=properties(c.getProperties());event("CHARACTERISTIC",c.getUuid()+";properties="+p);append("  CHAR  "+c.getUuid()+" ["+p+"]");for(BluetoothGattDescriptor d:c.getDescriptors()){event("DESCRIPTOR",d.getUuid().toString());append("    DESC  "+d.getUuid());}if((c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)readQueue.add(c);if((c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0)enableNotify(c);}}save.setEnabled(true);readNext();}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v,int st){String h=hex(v);event("READ","uuid="+c.getUuid()+";status="+st+";hex="+h);append("READ  "+c.getUuid()+" → "+h+"  ["+st+"]");readIndex++;handler.postDelayed(MainActivity.this::readNext,120);}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){String h=hex(v);event("NOTIFY","uuid="+c.getUuid()+";hex="+h);append("NOTIFY "+c.getUuid()+" → "+h);}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int st){event("DESCRIPTOR_WRITE","uuid="+d.getUuid()+";status="+st);}
    };
    @SuppressLint("MissingPermission") private void readNext(){if(gatt!=null&&readIndex<readQueue.size()){gatt.readCharacteristic(readQueue.get(readIndex));}else{event("CAPTURE_READY","reads="+readQueue.size()+";notifications="+notifySet.size());append("=== CAPTURE READY ===");status.setText("● Capture hazır — CAPTURE'a bas");}}
    @SuppressLint("MissingPermission") private void enableNotify(BluetoothGattCharacteristic c){String u=c.getUuid().toString();if(notifySet.contains(u)||gatt==null)return;notifySet.add(u);boolean local=gatt.setCharacteristicNotification(c,true);event("NOTIFY_ENABLE",u+";local="+local);BluetoothGattDescriptor d=c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));if(d!=null){boolean ind=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0;d.setValue(ind?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);gatt.writeDescriptor(d);}}

    private void newCapture(){capture.setLength(0);firstEvent=true;notifySet.clear();readQueue.clear();readIndex=0;capture.append("{\n  \"capture_version\":3,\n  \"started\":\"").append(ts()).append("\",\n  \"events\":[\n");}
    private void event(String type,String data){synchronized(capture){if(!firstEvent)capture.append(",\n");firstEvent=false;capture.append("    {\"time\":\"").append(esc(ts())).append("\",\"type\":\"").append(esc(type)).append("\",\"data\":\"").append(esc(data)).append("\"}");}}
    private void saveCapture(){String body=capture.toString()+"\n  ],\n  \"device\":{\"name\":\""+esc(selectedName)+"\",\"address\":\""+esc(selectedAddress)+"\"},\n  \"finished\":\""+ts()+"\"\n}\n";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"buds2_capture_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".json");pendingExport=body;startActivityForResult(i,CREATE_FILE);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==CREATE_FILE&&c==RESULT_OK&&d!=null){try(OutputStream o=getContentResolver().openOutputStream(d.getData())){o.write(pendingExport.getBytes("UTF-8"));append("CAPTURE SAVED → "+d.getData());status.setText("● Capture kaydedildi");}catch(Exception e){append("SAVE ERROR "+e);}}}

    private String properties(int p){StringBuilder s=new StringBuilder();if((p&BluetoothGattCharacteristic.PROPERTY_READ)!=0)s.append("READ ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)s.append("WRITE ");if((p&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)s.append("WRITE_NR ");if((p&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)s.append("NOTIFY ");if((p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)s.append("INDICATE ");if((p&BluetoothGattCharacteristic.PROPERTY_BROADCAST)!=0)s.append("BROADCAST ");return s.toString().trim();}
    private String gattStatus(int s){switch(s){case 0:return"GATT_SUCCESS";case 8:return"GATT_CONN_TIMEOUT";case 19:return"GATT_CONN_TERMINATE_PEER_USER";case 22:return"GATT_CONN_TERMINATE_LOCAL_HOST";case 133:return"GATT_ERROR";case 257:return"GATT_FAILURE/CONNECTION_CANCELLED";default:return"GATT_STATUS_"+s;}}
    private String hex(byte[] v){if(v==null)return"null";StringBuilder s=new StringBuilder();for(byte b:v)s.append(String.format(Locale.US,"%02X ",b&255));return s.toString().trim();}
    @SuppressLint("MissingPermission") private String safeName(BluetoothDevice d){try{return d.getName()==null?"(isimsiz)":d.getName();}catch(Exception e){return"(izin yok)";}}
    @SuppressLint("MissingPermission") private String safeAddress(BluetoothDevice d){try{return d.getAddress();}catch(Exception e){return"unknown";}}
    private String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}private String ts(){return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US).format(new Date());}
    private void append(String s){runOnUiThread(()->log.append(new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+"  "+s+"\n"));}
    private void msg(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());append(s);}
    @Override protected void onDestroy(){if(scanner!=null)try{scanner.stopScan(scanCallback);}catch(Exception ignored){}closeGatt();super.onDestroy();}
}
