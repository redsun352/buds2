package com.yigit.buds2blescanner.protocol;

/** Immutable snapshot of information decoded from Buds2 status frames. */
public final class Buds2DeviceState {
    public final int leftBattery;
    public final int rightBattery;
    public final int caseBattery;
    public final int wearing;
    public final int ambient;
    public final int ambientVolume;
    public final int eq;
    public final int eqType;
    public final int mainEar;
    public final int connectionState;
    public final String firmwareRaw;

    private Buds2DeviceState(Builder b) {
        leftBattery = b.leftBattery;
        rightBattery = b.rightBattery;
        caseBattery = b.caseBattery;
        wearing = b.wearing;
        ambient = b.ambient;
        ambientVolume = b.ambientVolume;
        eq = b.eq;
        eqType = b.eqType;
        mainEar = b.mainEar;
        connectionState = b.connectionState;
        firmwareRaw = b.firmwareRaw;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int leftBattery=-1, rightBattery=-1, caseBattery=-1;
        private int wearing=-1, ambient=-1, ambientVolume=-1, eq=-1, eqType=-1;
        private int mainEar=-1, connectionState=-1;
        private String firmwareRaw="";

        public Builder leftBattery(int v){leftBattery=v;return this;}
        public Builder rightBattery(int v){rightBattery=v;return this;}
        public Builder caseBattery(int v){caseBattery=v;return this;}
        public Builder wearing(int v){wearing=v;return this;}
        public Builder ambient(int v){ambient=v;return this;}
        public Builder ambientVolume(int v){ambientVolume=v;return this;}
        public Builder eq(int v){eq=v;return this;}
        public Builder eqType(int v){eqType=v;return this;}
        public Builder mainEar(int v){mainEar=v;return this;}
        public Builder connectionState(int v){connectionState=v;return this;}
        public Builder firmwareRaw(String v){firmwareRaw=v==null?"":v;return this;}
        public Buds2DeviceState build(){return new Buds2DeviceState(this);}
    }
}
