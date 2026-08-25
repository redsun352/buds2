package com.yigit.buds2blescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_BT = 1001;
    private static final long SCAN_TIME_MS = 10000L;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView log;
    private Button scanButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        buildUi();
        requestPermissionsIfNeeded();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(12);
        root.setPadding(p, p, p, p);

        status = new TextView(this);
        status.setText("Hazır");
        status.setTextSize(16);
        root.addView(status);

        scanButton = new Button(this);
        scanButton.setText("BLE TARA");
        scanButton.setOnClickListener(v -> startScan());
        root.addView(scanButton);

        ScrollView scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextSize(12);
        log.setTextIsSelectable(true);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
            }
        } else if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BT);
        }
    }

    @SuppressLint("MissingPermission")
    private boolean ready() {
        if (adapter == null) {
            append("Bluetooth desteklenmiyor.");
            return false;
        }
        if (!adapter.isEnabled()) {
            append("Bluetooth kapalı.");
            return false;
        }
        return Build.VERSION.SDK_INT < 31 ||
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                 checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (!ready()) return;
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            append("BLE scanner alınamadı.");
            return;
        }
        append("\n=== BLE SCAN ===");
        status.setText("Tarama yapılıyor...");
        scanButton.setEnabled(false);
        scanner.startScan(null,
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback);
        handler.postDelayed(this::stopScan, SCAN_TIME_MS);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanner != null) scanner.stopScan(callback);
        scanButton.setEnabled(true);
        status.setText("Tarama tamamlandı");
    }

    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String name = "";
            try { name = d.getName(); } catch (SecurityException ignored) {}
            if (name == null || name.isEmpty()) name = "(isimsiz)";
            String line = String.format(Locale.US, "\n[%s] %s | RSSI %d", name, d.getAddress(), result.getRssi());
            append(line);
            if (name.toLowerCase(Locale.ROOT).contains("buds")) connect(d);
        }

        @Override public void onScanFailed(int errorCode) {
            append("Scan error: " + errorCode);
            scanButton.setEnabled(true);
        }
    };

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        stopScan();
        if (gatt != null) { gatt.close(); gatt = null; }
        append("=== GATT CONNECT ===");
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                append("Bağlandı; servisler keşfediliyor...");
                g.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                append("Bağlantı kesildi: " + statusCode);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            append("\n=== GATT SERVICES ===");
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                append("Service discovery başarısız: " + statusCode);
                return;
            }
            for (BluetoothGattService service : g.getServices()) {
                append("SERVICE " + service.getUuid());
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    append("  CHAR " + c.getUuid() + " properties=" + properties(c.getProperties()));
                }
            }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            append("NOTIFY " + c.getUuid() + " -> " + hex(value));
        }

        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int statusCode) {
            append("READ " + c.getUuid() + " -> " + hex(value) + " status=" + statusCode);
        }
    };

    private String properties(int p) {
        StringBuilder s = new StringBuilder();
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) s.append("READ ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) s.append("WRITE ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) s.append("WRITE_NR ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) s.append("NOTIFY ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) s.append("INDICATE ");
        return s.toString().trim();
    }

    private String hex(byte[] value) {
        if (value == null) return "null";
        StringBuilder s = new StringBuilder();
        for (byte b : value) s.append(String.format(Locale.US, "%02X ", b & 0xFF));
        return s.toString().trim();
    }

    private void append(String text) {
        runOnUiThread(() -> log.append(text + "\n"));
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        stopScan();
        if (gatt != null) { gatt.disconnect(); gatt.close(); }
        super.onDestroy();
    }
}
