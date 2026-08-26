package com.yigit.buds2blescanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import com.yigit.buds2blescanner.protocol.Ayf1FirmwareProfile;

/** Professional shell for the SM-R177 AYF1 Buds2 toolchain. */
public class ProfessionalDashboardActivity extends Activity {
    private LinearLayout content;
    private TextView pageTitle, connection;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); build(); showDashboard();
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s, float size) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(dp(12),dp(8),dp(12),dp(8)); return t; }
    private Button nav(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private TextView card(String title,String value){ TextView t=label(title+"\n"+value,15); t.setPadding(dp(16),dp(14),dp(16),dp(14)); return t; }

    private void build() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        pageTitle=label("SOUND STUDIO PRO",24); pageTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        connection=label("● AYF1 Protocol Engine hazır",13); header.addView(pageTitle); header.addView(connection); root.addView(header);
        HorizontalScrollView navScroll=new HorizontalScrollView(this); LinearLayout navBar=new LinearLayout(this); navBar.setOrientation(LinearLayout.HORIZONTAL);
        Button bHome=nav("Dashboard"),bBuds=nav("Buds2"),bStudio=nav("Sound Studio"),bCapture=nav("Capture"),bProtocol=nav("Protocol"),bFirmware=nav("AYF1 Firmware"),bDiag=nav("Diagnostics"),bSettings=nav("Settings");
        navBar.addView(bHome);navBar.addView(bBuds);navBar.addView(bStudio);navBar.addView(bCapture);navBar.addView(bProtocol);navBar.addView(bFirmware);navBar.addView(bDiag);navBar.addView(bSettings);navScroll.addView(navBar);root.addView(navScroll);
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        bHome.setOnClickListener(v->showDashboard());bBuds.setOnClickListener(v->showBuds());bStudio.setOnClickListener(v->showStudio());bCapture.setOnClickListener(v->showCapture());bProtocol.setOnClickListener(v->showProtocol());bFirmware.setOnClickListener(v->showFirmware());bDiag.setOnClickListener(v->showDiagnostics());bSettings.setOnClickListener(v->showSettings());
        setContentView(root);
    }
    private void clear(String title){pageTitle.setText(title);content.removeAllViews();}

    private void showDashboard(){
        clear("SOUND STUDIO PRO");
        content.addView(card("DEVICE","Galaxy Buds2 / SM-R177\nFirmware: "+Ayf1FirmwareProfile.VERSION));
        content.addView(card("CONNECTION","Bluetooth Classic → RFCOMM/SPP\nRX decoder active · TX unverified commands blocked"));
        content.addView(card("AUDIO ENGINE","24-band Parametric EQ · FFT · Dynamic EQ · Target Curves"));
        content.addView(card("FIRMWARE KNOWLEDGE","AYF1 feature map loaded: "+Ayf1FirmwareProfile.features().size()+" feature groups"));
        Button open=nav("OPEN BUDS2 DEVICE MANAGER");open.setOnClickListener(v->startActivity(new Intent(this,BudsManagerActivity.class)));content.addView(open);
    }
    private void showBuds(){
        clear("BUDS2 DEVICE");
        content.addView(card("IDENTITY","SM-R177 · Galaxy Buds2\nFirmware: R177XXU0AYF1"));
        content.addView(card("TRANSPORT","RFCOMM/SPP\nUUID 00001101-0000-1000-8000-00805F9B34FB"));
        content.addView(card("STATE","Battery · Wearing · Ambient · EQ · Touch · Firmware · Connection"));
        Button open=nav("DEVICE MANAGER");open.setOnClickListener(v->startActivity(new Intent(this,BudsManagerActivity.class)));content.addView(open);
    }
    private void showStudio(){
        clear("SOUND STUDIO");
        content.addView(card("PARAMETRIC EQ","24 bands\nFrequency · Gain · Q · Filter type · Preamp"));
        content.addView(card("ANALYZER","FFT 4096 / 8192 / 16384 / 32768\nPeak · RMS · Peak Hold · Spectrogram"));
        content.addView(card("DYNAMIC PROCESSING","Dynamic EQ · Spectral analysis · Auto headroom"));
        content.addView(card("REFERENCE","Studio Neutral · Warm · Bright · Vocal · Cinema · Gaming · Custom"));
        content.addView(label("Android DSP is separate from native Buds firmware controls.",12));
    }
    private void showCapture(){
        clear("CAPTURE LAB");
        content.addView(card("RX/TX","RFCOMM stream capture with direction preserved."));
        content.addView(card("FRAME","FD · header · message ID · payload · CRC16-CCITT · DD"));
        content.addView(card("DISCOVERY","Message ID · CRC · shape · changing offsets · RX/TX correlation"));
        content.addView(card("SAFETY","Unknown TX payloads are captured but never guessed or transmitted."));
    }
    private void showProtocol(){
        clear("PROTOCOL LAB");
        content.addView(card("VERIFIED RX","0x41 Metering · 0x60 Status · 0x61 Extended Status · 0x62 Connection · 0x63 Version"));
        content.addView(card("AYF1 FAMILIES","Noise · Ambient · EQ · Game Mode · Touch · Find My · Spatial · Diagnostics"));
        content.addView(card("COMMAND POLICY","Firmware evidence does not make a TX packet verified. Real TX capture is required."));
        content.addView(card("CRC","CRC16-CCITT calculated and compared for every complete frame."));
    }
    private void showFirmware(){
        clear("AYF1 FIRMWARE");
        content.addView(card("TARGET",Ayf1FirmwareProfile.summary()));
        content.addView(card("FLASH","BASE 0x28000000\nSIZE 0x800000\nOTA CODE 0x18000\nOTA REMAP 0x320000"));
        content.addView(card("IMAGE CRC32","0x"+Long.toHexString(Ayf1FirmwareProfile.IMAGE_CRC32).toUpperCase()));
        StringBuilder features=new StringBuilder();
        for(String name:Ayf1FirmwareProfile.features().keySet()) features.append("• ").append(name).append('\n');
        content.addView(card("FEATURES DISCOVERED",features.toString().trim()));
        content.addView(label("Read-only firmware knowledge. No flashing or firmware modification is performed here.",12));
    }
    private void showDiagnostics(){
        clear("DIAGNOSTICS");
        content.addView(card("FIRMWARE SELF-TEST","AYF1 contains SELF_TEST, EARBUDS_FIT_TEST and audio/microphone loopback diagnostics."));
        content.addView(card("ANC DIAGNOSTICS","ANC leak detection and ANC loopback functions are present in firmware."));
        content.addView(card("SENSORS","Spatial audio / gyro calibration interfaces are present in AYF1."));
        content.addView(card("STATUS","Passive diagnostics are safe. Active test commands remain locked until their TX payload is verified."));
    }
    private void showSettings(){
        clear("SETTINGS");
        content.addView(card("TARGET","SM-R177 / R177XXU0AYF1"));
        content.addView(card("TRANSPORT","RFCOMM / SPP preferred for Buds2 control"));
        content.addView(card("CAPTURE","Raw RX + TX + decoded frames + timestamps"));
        content.addView(card("SAFETY","Automatic response OFF · unknown TX blocked · firmware analysis read-only"));
    }
}
