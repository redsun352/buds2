package com.yigit.buds2blescanner.pro;

public final class Buds2DeviceState {
    private int leftBattery = -1, rightBattery = -1, caseBattery = -1;
    private int wearingCode = -1;
    private boolean wearing, ambient, coupled, classicMain;
    private int ambientVolume = -1, eq = -1, eqType = -1;
    private String model = "SM-R177", classicAddress = "", firmware = "";
    private Buds2ConnectionState connection = Buds2ConnectionState.DISCONNECTED;

    public synchronized void setBattery(int left, int right, int box) { leftBattery=left; rightBattery=right; caseBattery=box; }
    public synchronized void setWearing(boolean v) { wearing=v; wearingCode=v ? 17 : 0; }
    public synchronized void setWearing(int code) { wearingCode=code; wearing=code != 0; }
    public synchronized void setAmbient(boolean v, int volume) { ambient=v; ambientVolume=volume; }
    public synchronized void setCoupled(boolean v) { coupled=v; }
    public synchronized void setClassicMain(boolean v) { classicMain=v; }
    public synchronized void setEq(int value, int type) { eq=value; eqType=type; }
    public synchronized void setClassicAddress(String v) { classicAddress=v == null ? "" : v; }
    public synchronized void setFirmware(String v) { firmware=v == null ? "" : v; }
    public synchronized void setConnection(Buds2ConnectionState v) { connection=v; }
    public synchronized int getLeftBattery(){return leftBattery;}
    public synchronized int getRightBattery(){return rightBattery;}
    public synchronized int getCaseBattery(){return caseBattery;}
    public synchronized boolean isWearing(){return wearing;}
    public synchronized int getWearingCode(){return wearingCode;}
    public synchronized boolean isAmbient(){return ambient;}
    public synchronized int getAmbientVolume(){return ambientVolume;}
    public synchronized int getEq(){return eq;}
    public synchronized int getEqType(){return eqType;}
    public synchronized boolean isCoupled(){return coupled;}
    public synchronized boolean isClassicMain(){return classicMain;}
    public synchronized String getModel(){return model;}
    public synchronized String getClassicAddress(){return classicAddress;}
    public synchronized String getFirmware(){return firmware;}
    public synchronized Buds2ConnectionState getConnection(){return connection;}
}
