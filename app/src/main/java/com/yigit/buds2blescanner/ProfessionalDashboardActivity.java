package com.yigit.buds2blescanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/**
 * Professional shell for the Buds2 toolchain. The dashboard is intentionally
 * independent from the protocol transport so UI changes cannot break the
 * RFCOMM session. Each module can later be replaced without changing the
 * device layer.
 */
public class ProfessionalDashboardActivity extends Activity {
    private LinearLayout content;
    private TextView pageTitle, connection;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        build();
        showDashboard();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }
    private Button nav(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }
    private TextView card(String title, String value) {
        TextView t = label(title + "\n" + value, 15);
        t.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        return t;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        pageTitle = label("SOUND STUDIO PRO", 24);
        pageTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connection = label("● Sistem hazır | Buds2 Protocol Engine", 13);
        header.addView(pageTitle); header.addView(connection);
        root.addView(header);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        Button bHome = nav("Dashboard");
        Button bBuds = nav("Buds2");
        Button bStudio = nav("Sound Studio");
        Button bCapture = nav("Capture");
        Button bProtocol = nav("Protocol");
        Button bSettings = nav("Settings");
        navBar.addView(bHome); navBar.addView(bBuds); navBar.addView(bStudio);
        navBar.addView(bCapture); navBar.addView(bProtocol); navBar.addView(bSettings);
        navScroll.addView(navBar); root.addView(navScroll);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bHome.setOnClickListener(v -> showDashboard());
        bBuds.setOnClickListener(v -> showBuds());
        bStudio.setOnClickListener(v -> showStudio());
        bCapture.setOnClickListener(v -> showCapture());
        bProtocol.setOnClickListener(v -> showProtocol());
        bSettings.setOnClickListener(v -> showSettings());
        setContentView(root);
    }

    private void clear(String title) {
        pageTitle.setText(title); content.removeAllViews();
    }

    private void showDashboard() {
        clear("SOUND STUDIO PRO");
        content.addView(card("DEVICE", "Galaxy Buds2 / SM-R177\nTransport: Bluetooth Classic → RFCOMM/SPP"));
        content.addView(card("CONNECTION", "Ready — no control packet is sent automatically"));
        content.addView(card("AUDIO ENGINE", "24-band Parametric EQ · FFT · Dynamic EQ · Target Curves"));
        content.addView(card("PROTOCOL", "FD/DD framing · CRC16-CCITT · RX capture · verified-command registry"));
        Button open = nav("OPEN BUDS2 DEVICE MANAGER");
        open.setOnClickListener(v -> startActivity(new Intent(this, BudsManagerActivity.class)));
        content.addView(open);
    }

    private void showBuds() {
        clear("BUDS2 DEVICE");
        content.addView(card("IDENTITY", "SM-R177\nBLE identity and Classic/SPP identity are tracked separately."));
        content.addView(card("TRANSPORT", "RFCOMM/SPP\nUUID 00001101-0000-1000-8000-00805F9B34FB"));
        content.addView(card("STATE", "Battery · Wearing · Ambient · EQ · Firmware · Connection"));
        Button open = nav("DEVICE MANAGER");
        open.setOnClickListener(v -> startActivity(new Intent(this, BudsManagerActivity.class)));
        content.addView(open);
    }

    private void showStudio() {
        clear("SOUND STUDIO");
        content.addView(card("PARAMETRIC EQ", "24 bands\nFrequency · Gain · Q · Filter type · Preamp"));
        content.addView(card("ANALYZER", "FFT 4096 / 8192 / 16384 / 32768\nPeak · RMS · Peak Hold · Spectrogram"));
        content.addView(card("DYNAMIC PROCESSING", "Dynamic EQ · Spectral analysis · Auto headroom"));
        content.addView(card("REFERENCE", "Studio Neutral · Warm · Bright · Vocal · Cinema · Gaming · Custom"));
        content.addView(label("Native Buds2 controls and Android DSP remain separate.", 12));
    }

    private void showCapture() {
        clear("CAPTURE LAB");
        content.addView(card("RX", "RFCOMM frames decoded by length, not by the first DD byte."));
        content.addView(card("FRAME", "FD · uint16 LE header · message ID · payload · CRC16 · DD"));
        content.addView(card("ANALYSIS", "Message ID · CRC validity · shape · variable byte offsets · decoded state"));
        content.addView(card("EXPORT", "Structured JSON lines + raw hexadecimal capture"));
        content.addView(label("TX commands are disabled until their payloads are verified against real captures.", 12));
    }

    private void showProtocol() {
        clear("PROTOCOL LAB");
        content.addView(card("VERIFIED RX", "0x41 Metering · 0x60 Status · 0x61 Extended Status · 0x62 Connection · 0x63 Version"));
        content.addView(card("CONTROL FAMILIES", "Noise · Ambient · EQ · Game Mode · Touch · Find My · FOTA"));
        content.addView(card("SAFETY", "Unknown TX payloads are not guessed or transmitted."));
        content.addView(card("CRC", "CRC16-CCITT calculated and compared for every complete frame."));
    }

    private void showSettings() {
        clear("SETTINGS");
        content.addView(card("TRANSPORT", "RFCOMM / SPP preferred for Buds2 control"));
        content.addView(card("CAPTURE", "Raw RX + decoded frames + timestamps"));
        content.addView(card("SAFETY", "Automatic response: OFF until protocol payload is verified"));
        content.addView(card("DSP", "Android-side audio processing is independent from Buds firmware controls"));
    }
}
