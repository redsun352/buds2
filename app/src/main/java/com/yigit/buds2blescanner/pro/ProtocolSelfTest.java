package com.yigit.buds2blescanner.pro;

import java.util.ArrayList;
import java.util.List;

/** Deterministic self-tests for framing and automatic discovery. */
public final class ProtocolSelfTest {
    public static final class Result {
        public final String name;
        public final boolean passed;
        public final String detail;
        Result(String n, boolean p, String d) { name=n; passed=p; detail=d; }
    }

    private ProtocolSelfTest() {}

    public static List<Result> run() {
        List<Result> out = new ArrayList<>();
        try {
            testStreamSplit(out);
            testMultipleFrames(out);
            testNoiseRecovery(out);
            testDiscovery(out);
        } catch (Throwable t) {
            out.add(new Result("unexpected_exception", false, t.toString()));
        }
        return out;
    }

    private static void testStreamSplit(List<Result> out) {
        ProtocolAnalyzer p = new ProtocolAnalyzer();
        byte[] f = sampleFrame(0x60, new byte[]{1,2,3});
        p.feed(ProtocolAnalyzer.Direction.RX, slice(f,0,3), 1);
        List<ProtocolAnalyzer.FrameEvent> r = p.feed(ProtocolAnalyzer.Direction.RX, slice(f,3,f.length), 2);
        check(out, "split_frame", r.size()==1 && r.get(0).messageId==0x60, "frames="+r.size());
    }

    private static void testMultipleFrames(List<Result> out) {
        ProtocolAnalyzer p = new ProtocolAnalyzer();
        byte[] a = sampleFrame(0x60, new byte[]{1});
        byte[] b = sampleFrame(0x61, new byte[]{2,3});
        byte[] both = new byte[a.length+b.length];
        System.arraycopy(a,0,both,0,a.length); System.arraycopy(b,0,both,a.length,b.length);
        List<ProtocolAnalyzer.FrameEvent> r = p.feed(ProtocolAnalyzer.Direction.RX,both,3);
        check(out,"multiple_frames",r.size()==2 && r.get(0).messageId==0x60 && r.get(1).messageId==0x61,"frames="+r.size());
    }

    private static void testNoiseRecovery(List<Result> out) {
        ProtocolAnalyzer p = new ProtocolAnalyzer();
        byte[] f = sampleFrame(0x63, new byte[]{7,8});
        byte[] x = new byte[f.length+2]; x[0]=0x11; x[1]=0x22; System.arraycopy(f,0,x,2,f.length);
        List<ProtocolAnalyzer.FrameEvent> r = p.feed(ProtocolAnalyzer.Direction.RX,x,4);
        check(out,"noise_recovery",r.size()==1 && r.get(0).messageId==0x63,"frames="+r.size());
    }

    private static void testDiscovery(List<Result> out) {
        ProtocolAnalyzer p = new ProtocolAnalyzer();
        ProtocolDiscoveryEngine d = new ProtocolDiscoveryEngine();
        d.observeAll(p.feed(ProtocolAnalyzer.Direction.RX,sampleFrame(0x60,new byte[]{1,9,3}),10));
        d.observeAll(p.feed(ProtocolAnalyzer.Direction.RX,sampleFrame(0x60,new byte[]{1,8,3}),11));
        boolean ok = d.discoverFields().containsKey(0x60);
        check(out,"field_discovery",ok,"message=0x60");
    }

    private static byte[] sampleFrame(int msg, byte[] payload) {
        // Minimal synthetic frame used only to test framing. CRC is intentionally not asserted here.
        int length = 1 + payload.length + 2;
        byte[] f = new byte[length+5];
        f[0]=(byte)0xFD; f[1]=(byte)((length>>>8)&0x3F); f[2]=(byte)(length&0xFF); f[3]=(byte)msg;
        System.arraycopy(payload,0,f,4,payload.length);
        f[f.length-1]=(byte)0xDD;
        return f;
    }
    private static byte[] slice(byte[] a,int from,int to){byte[] r=new byte[to-from];System.arraycopy(a,from,r,0,r.length);return r;}
    private static void check(List<Result> out,String n,boolean p,String d){out.add(new Result(n,p,d));}
}
