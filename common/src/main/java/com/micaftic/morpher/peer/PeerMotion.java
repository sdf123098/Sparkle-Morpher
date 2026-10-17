package com.micaftic.morpher.peer;

import java.io.*;
import java.util.*;

/** Model/revision-bound state. Ages are monotonic durations; no expression strings or Cloud identities. */
public record PeerMotion(long appearanceRevision,long sequence,long eventSequence,String action,long actionAgeMillis,
        int ttlMillis,Map<String,Float> roaming,Map<String,Float> modelVariables,Map<String,Controller> controllers,List<NumericEvent> events) {
    public static final int BYTES=8192;
    public PeerMotion(long revision,long sequence,long event,String action,long age,int ttl,Map<String,Float> roaming,Map<String,Controller> controllers,List<NumericEvent> events){this(revision,sequence,event,action,age,ttl,roaming,Map.of(),controllers,events);}
    public record Controller(long transitionSequence,String state,long ageMillis,Map<String,Float> variables) {
        public Controller {if(transitionSequence<0)throw new IllegalArgumentException("Invalid controller sequence");text(state,128);age(ageMillis);variables=numbers(variables,8);}
    }
    public record NumericEvent(long sequence,long ageMillis,List<Float> values,Map<String,Float> assignments) {
        public NumericEvent(long sequence,long ageMillis,List<Float> values){this(sequence,ageMillis,values,Map.of());}
        public NumericEvent {if(sequence<1)throw new IllegalArgumentException("Invalid numeric event sequence");age(ageMillis);values=List.copyOf(values);if(values.size()>16)throw new IllegalArgumentException("Numeric event too large");values.forEach(PeerMotion::finite);assignments=numbers(assignments,8);if(!assignments.keySet().stream().allMatch(PeerNumericAssignments::target)||!assignments.isEmpty()&&!values.isEmpty())throw new IllegalArgumentException("Invalid numeric event targets");}
    }
    public PeerMotion {
        if(appearanceRevision<1||sequence<1||eventSequence<0||ttlMillis<250||ttlMillis>10000)throw new IllegalArgumentException("Invalid motion revision/sequence/TTL");
        text(action,256);age(actionAgeMillis);roaming=numbers(roaming,64);controllers=Map.copyOf(controllers);events=List.copyOf(events);
        modelVariables=numbers(modelVariables,16);if(!modelVariables.keySet().stream().allMatch(PeerNumericAssignments::target))throw new IllegalArgumentException("Invalid model variables");
        if(controllers.size()>16||events.size()>4)throw new IllegalArgumentException("Motion collection exceeds budget");controllers.keySet().forEach(key->text(key,128));
    }
    private static void text(String value,int bytes){if(value==null||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>bytes||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid motion text");}
    private static void age(long millis){if(millis<0||millis>86_400_000L)throw new IllegalArgumentException("Invalid motion age");}
    private static void finite(Float value){if(value==null||!Float.isFinite(value))throw new IllegalArgumentException("Invalid motion number");}
    private static Map<String,Float> numbers(Map<String,Float> values,int limit){
        if(values.size()>limit)throw new IllegalArgumentException("Too many motion variables");values.forEach((key,value)->{if(key==null||!key.matches("[A-Za-z_][A-Za-z0-9_.]{0,63}")||key.contains(".."))throw new IllegalArgumentException("Invalid motion variable");finite(value);if(Math.abs(value)>1_000_000f)throw new IllegalArgumentException("Motion variable exceeds range");});return Map.copyOf(values);
    }
    public byte[] encode()throws IOException {
        var payload=PeerRoomCodec.encode(out->{
            out.writeLong(appearanceRevision);out.writeLong(sequence);out.writeLong(eventSequence);PeerRoomCodec.text(out,action,256);out.writeLong(actionAgeMillis);out.writeInt(ttlMillis);writeNumbers(out,roaming);writeNumbers(out,modelVariables);
            out.writeByte(controllers.size());for(var entry:new TreeMap<>(controllers).entrySet()){PeerRoomCodec.text(out,entry.getKey(),128);var state=entry.getValue();out.writeLong(state.transitionSequence);PeerRoomCodec.text(out,state.state,128);out.writeLong(state.ageMillis);writeNumbers(out,state.variables);}
            out.writeByte(events.size());for(var event:events){out.writeLong(event.sequence);out.writeLong(event.ageMillis);out.writeByte(event.values.size());for(float value:event.values)out.writeFloat(value);writeNumbers(out,event.assignments);}
        });if(payload.length>BYTES)throw new IOException("Peer motion exceeds byte budget");return payload;
    }
    public static PeerMotion decode(byte[] bytes)throws IOException {
        if(bytes.length>BYTES)throw new IOException("Peer motion exceeds byte budget");
        return PeerRoomCodec.decode(bytes,in->{
            long revision=in.readLong(),seq=in.readLong(),event=in.readLong();String action=PeerRoomCodec.text(in,256);long age=in.readLong();int ttl=in.readInt();var roaming=readNumbers(in,64);var modelVariables=readNumbers(in,16);
            int count=in.readUnsignedByte();if(count>16)throw new IOException("Too many motion controllers");Map<String,Controller> controllers=new LinkedHashMap<>();
            for(int i=0;i<count;i++){String name=PeerRoomCodec.text(in,128);var state=new Controller(in.readLong(),PeerRoomCodec.text(in,128),in.readLong(),readNumbers(in,8));if(controllers.put(name,state)!=null)throw new IOException("Duplicate controller");}
            int n=in.readUnsignedByte();if(n>4)throw new IOException("Too many numeric events");List<NumericEvent> events=new ArrayList<>();Set<Long> seen=new HashSet<>();
            for(int i=0;i<n;i++){long id=in.readLong(),elapsed=in.readLong();int values=in.readUnsignedByte();if(values>16||!seen.add(id))throw new IOException("Invalid numeric event");List<Float> args=new ArrayList<>();for(int j=0;j<values;j++)args.add(in.readFloat());events.add(new NumericEvent(id,elapsed,args,readNumbers(in,8)));}
            return new PeerMotion(revision,seq,event,action,age,ttl,roaming,modelVariables,controllers,events);
        });
    }
    private static void writeNumbers(DataOutput out,Map<String,Float> values)throws IOException {out.writeByte(values.size());for(var entry:new TreeMap<>(values).entrySet()){PeerRoomCodec.text(out,entry.getKey(),64);out.writeFloat(entry.getValue());}}
    private static Map<String,Float> readNumbers(DataInput in,int limit)throws IOException {int count=in.readUnsignedByte();if(count>limit)throw new IOException("Too many motion numbers");Map<String,Float> values=new LinkedHashMap<>();for(int i=0;i<count;i++)if(values.put(PeerRoomCodec.text(in,64),in.readFloat())!=null)throw new IOException("Duplicate motion number");return values;}
}
