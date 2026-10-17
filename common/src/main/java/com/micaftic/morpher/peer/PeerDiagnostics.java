package com.micaftic.morpher.peer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded diagnostic observations, not GPU fence or visibility assertions. */
public final class PeerDiagnostics {
    public static final LongAdder bytesSent=new LongAdder(),bytesReceived=new LongAdder(),hotCacheHits=new LongAdder(),resumedBytes=new LongAdder();
    private static final Map<String,Long> textureSubmissions=new ConcurrentHashMap<>();
    private static final Map<String,String> uploadFailures=new ConcurrentHashMap<>();
    public static void uploadFailed(String model,RuntimeException failure){if(model!=null&&model.startsWith("peer:")&&(uploadFailures.size()<128||uploadFailures.containsKey(model)))uploadFailures.put(model,failure.getClass().getSimpleName());}
    public static String uploadFailure(String model){return uploadFailures.get(model);}
    public static final class Timeline {
        private final long began=System.nanoTime();private final EnumMap<PeerObservation.State,Long> first=new EnumMap<>(PeerObservation.State.class);
        public synchronized void observe(PeerObservation.State state){first.putIfAbsent(state,System.nanoTime());}
        public synchronized String describe(){var text=new StringBuilder();first.forEach((state,time)->text.append(state).append('=').append(Math.max(0,(time-began)/1_000_000L)).append("ms "));return text.toString();}
    }
    private PeerDiagnostics(){}
    public static void textureUploadSubmitted(String model){if(model!=null&&model.startsWith("peer:")&&(textureSubmissions.size()<128||textureSubmissions.containsKey(model))){
            uploadFailures.remove(model);
            textureSubmissions.putIfAbsent(model,System.nanoTime());
            PeerFrameEvidence.GLOBAL.uploaded(model);
        }}
    public static Long textureSubmission(String model){return textureSubmissions.get(model);}
    public static void forget(String model){uploadFailures.remove(model);textureSubmissions.remove(model);PeerFrameEvidence.GLOBAL.forget(model);PeerFrameWork.PEER.forget(model);}
    public static void reset(){uploadFailures.clear();PeerFrameEvidence.GLOBAL.reset();PeerFrameWork.PEER.reset();textureSubmissions.clear();bytesSent.reset();bytesReceived.reset();hotCacheHits.reset();resumedBytes.reset();}
    public static String resources(){return "stream_reserved="+PeerResourceBudget.STREAM.used()+" decode_reserved="+PeerResourceBudget.DECODE.used()+" resident_cpu_estimate="+PeerResidentReservation.CPU.used()+" resident_gpu_estimate="+PeerResidentReservation.GPU.used()+" bytes_sent="+bytesSent.sum()+" bytes_received="+bytesReceived.sum()+" resumed_bytes="+resumedBytes.sum()+" hot_cache_hits="+hotCacheHits.sum()+" peak_stream_reserved="+PeerResourceBudget.STREAM.peak()+" peak_decode_reserved="+PeerResourceBudget.DECODE.peak()+" peak_cpu_reserved="+PeerResidentReservation.CPU.peak()+" peak_gpu_estimate_reserved="+PeerResidentReservation.GPU.peak()+" "+PeerFrameWork.PEER.diagnostics();}
}
