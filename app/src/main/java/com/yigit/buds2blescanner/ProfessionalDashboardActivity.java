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
        connection=label("● AYF1 Firmware Engine hazır",13); header.addView(pageTitle); header.addView(connection); root.addView(header);
        HorizontalScrollView navScroll=new HorizontalScrollView(this); LinearLayout navBar=new LinearLayout(this); navBar.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"Dashboard","Buds2","Sound Studio","Noise Control","Equalizer","Ambient","Touch","Conversation","Spatial","Find My","Fit Test","Diagnostics","Capture","Protocol","AYF1 Firmware","Settings"};
        Button[] buttons=new Button[names.length];
        for(int i=0;i<names.length;i++){ buttons[i]=nav(names[i]); navBar.addView(buttons[i]); }
        navScroll.addView(navBar); root.addView(navScroll);
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        buttons[0].setOnClickListener(v->showDashboard()); buttons[1].setOnClickListener(v->showBuds()); buttons[2].setOnClickListener(v->showStudio());
        buttons[3].setOnClickListener(v->showNoise()); buttons[4].setOnClickListener(v->showEqualizer()); buttons[5].setOnClickListener(v->showAmbient()); buttons[6].setOnClickListener(v->showTouch());
        buttons[7].setOnClickListener(v->showConversation()); buttons[8].setOnClickListener(v->showSpatial()); buttons[9].setOnClickListener(v->showFindMy()); buttons[10].setOnClickListener(v->showFitTest());
        buttons[11].setOnClickListener(v->showDiagnostics()); buttons[12].setOnClickListener(v->showCapture()); buttons[13].setOnClickListener(v->showProtocol()); buttons[14].setOnClickListener(v->showFirmware()); buttons[15].setOnClickListener(v->showSettings());
        setContentView(root);
    }
    private void clear(String title){pageTitle.setText(title);content.removeAllViews();}
    private void locked(String what){ content.addView(card("AYF1 CAPABILITY",what+"\nFirmware support detected. Active TX remains locked until wire format is verified.")); }

    private void showDashboard(){
        clear("SOUND STUDIO PRO");
        content.addView(card("DEVICE","Galaxy Buds2 / SM-R177\nFirmware: "+Ayf1FirmwareProfile.VERSION));
        content.addView(card("ENGINE","AYF1 firmware knowledge + RFCOMM protocol + capture discovery"));
        content.addView(card("AUDIO","24-band Parametric EQ · FFT · Dynamic EQ · Reference curves"));
        content.addView(card("CAPABILITIES",""+Ayf1FirmwareProfile.features().size()+" firmware feature groups loaded"));
        content.addView(card("SAFETY","Unknown TX commands blocked. Firmware knowledge is read-only."));
        Button open=nav("OPEN DEVICE MANAGER");open.setOnClickListener(v->startActivity(new Intent(this,BudsManagerActivity.class)));content.addView(open);
    }
    private void showBuds(){
        clear("BUDS2 DEVICE"); content.addView(card("IDENTITY","SM-R177 · Galaxy Buds2\nR177XXU0AYF1"));
        content.addView(card("TRANSPORT","Bluetooth Classic → RFCOMM/SPP\nUUID 00001101-0000-1000-8000-00805F9B34FB"));
        content.addView(card("LIVE STATE","Battery · Wearing · Ambient · EQ · Touch · Connection · Version"));
    }
    private void showStudio(){
        clear("SOUND STUDIO"); content.addView(card("PARAMETRIC EQ","24 bands · Frequency · Gain · Q · Filter type · Preamp"));
        content.addView(card("ANALYZER","FFT 4096 / 8192 / 16384 / 32768 · Peak · RMS · Hold · Spectrogram"));
        content.addView(card("DYNAMIC PROCESSING","Dynamic EQ · Spectral analysis · Auto headroom"));
        content.addView(card("REFERENCE CURVES","Studio Neutral · Warm · Bright · Vocal · Cinema · Gaming · Custom"));
    }
    private void showNoise(){ clear("NOISE CONTROL"); content.addView(card("ANC","On / Off · level · FF/FB gain evidence · leak detection")); content.addView(card("AMBIENT","On / Off · level · tone · custom ambient")); content.addView(card("ONE EARBUD","AYF1 contains SET_ANC_WITH_ONE_EARBUD capability")); locked("Noise control commands"); }
    private void showEqualizer(){ clear("EQUALIZER"); content.addView(card("AYF1","TwaAudio_EQSet · SET_EQUALIZER_MODE")); content.addView(card("STUDIO DSP","24-band parametric EQ is phone-side DSP and does not overwrite firmware EQ.")); content.addView(card("PRESETS","Samsung-like · Studio Neutral · Warm · Bright · Vocal · Cinema · Gaming · Custom")); locked("SET_EQUALIZER_MODE"); }
    private void showAmbient(){ clear("AMBIENT"); content.addView(card("FIRMWARE","AmbientSoundOn · Off · SetLevel · ControlLevel · ControlTone · SetTone")); content.addView(card("CONTROLS","Mode · Level · Tone · Custom Ambient · Ambient Volume")); locked("Ambient TX commands"); }
    private void showTouch(){ clear("TOUCH"); content.addView(card("FIRMWARE","TouchFunction · Repeat · NoiseControl · Set/Get/UpdateTouchControl · TouchLockTimer")); content.addView(card("GESTURES","Single tap · Double tap · Triple tap · Tap & hold · repeat/release events")); locked("Touch configuration commands"); }
    private void showConversation(){ clear("CONVERSATION"); content.addView(card("DETECTION","SET_DETECT_CONVERSATION · duration · SPEAK_SEAMLESSLY")); content.addView(card("CALL / WEARING","SET_CALLPATH_CTRL_BY_WEARING")); locked("Conversation detection TX"); }
    private void showSpatial(){ clear("SPATIAL AUDIO"); content.addView(card("FIRMWARE","Spatial sync · audio spatial data/control · Bluetooth spatial state")); content.addView(card("GYRO","Factory bias · in-use bias · any bias · inject bias evidence")); locked("Spatial/gyro active commands"); }
    private void showFindMy(){ clear("FIND MY EARBUDS"); content.addView(card("FIRMWARE","Start · Stop · Mute · Sync · Volume · Reset Volume · Place")); content.addView(card("MESSAGES","FIND_MY_EARBUDS_START · STOP · VOLUME_CONTROL")); locked("Find My active commands"); }
    private void showFitTest(){ clear("FIT TEST"); content.addView(card("FIRMWARE","EARBUDS_FIT_TEST · SESSION_FIT_TEST_FINISHED")); content.addView(card("AUDIO TESTS","ANC leak detection and microphone loopback capabilities detected.")); locked("Fit-test command"); }
    private void showDiagnostics(){
        clear("DIAGNOSTICS");
        content.addView(card("SELF TEST","TWU_MSG_ID_SELF_TEST"));
        content.addView(card("ACT TEST","Opening · command · send data · closing · charging · Hall IC · factory mode"));
        content.addView(card("MIC LOOPBACK","VP · FF · FB · ANC oneshot · Ambient oneshot"));
        content.addView(card("ANC LEAK","TwaAudioProd_ANCLeakdetect · TwaAudioUser_ANCLeakdetect"));
        content.addView(card("SENSORS","Spatial / gyro calibration interfaces"));
        locked("Active diagnostic commands");
    }
    private void showCapture(){ clear("CAPTURE LAB"); content.addView(card("RX/TX","RFCOMM stream capture with direction and timestamps.")); content.addView(card("FRAME","FD · header · message ID · payload · CRC16-CCITT · DD")); content.addView(card("OBSERVED","0x41 · 0x60 · 0x61 · 0x63 · 0xF5 · 0xF6")); content.addView(card("DISCOVERY","Offsets · variants · CRC · shape · RX/TX correlation")); }
    private void showProtocol(){ clear("PROTOCOL LAB"); content.addView(card("VERIFIED RX","Metering · Status · Extended Status · Version and connection telemetry")); content.addView(card("DISCOVERY","F5/F6 variants · changing fields · response correlation")); content.addView(card("COMMAND GATE","Firmware symbol alone is never enough to authorize TX.")); }
    private void showFirmware(){
        clear("AYF1 FIRMWARE"); content.addView(card("TARGET",Ayf1FirmwareProfile.summary())); content.addView(card("FLASH","BASE 0x28000000 · SIZE 0x800000 · OTA CODE 0x18000 · OTA REMAP 0x320000")); content.addView(card("CRC32","0x"+Long.toHexString(Ayf1FirmwareProfile.IMAGE_CRC32).toUpperCase()));
        StringBuilder features=new StringBuilder(); for(String name:Ayf1FirmwareProfile.features().keySet()) features.append("• ").append(name).append('\n'); content.addView(card("FEATURE CATALOG",features.toString().trim()));
        content.addView(label("AYF1 is the capability source. The application never modifies or flashes the firmware.",12));
    }
    private void showSettings(){ clear("SETTINGS"); content.addView(card("TARGET","SM-R177 / R177XXU0AYF1")); content.addView(card("TRANSPORT","RFCOMM / SPP preferred")); content.addView(card("CAPTURE","Raw RX + TX + decoded frames + timestamps")); content.addView(card("SAFETY","Unknown TX blocked · firmware read-only · active diagnostics locked until verified")); }
}
