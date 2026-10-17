package com.micaftic.morpher.peer;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import java.util.*;
import java.util.regex.Pattern;

/** Model-declared allow-list only; packets cannot add code or controller states. */
public record PeerModelPolicy(Set<String> actions,Map<String,Set<String>> controllers,Set<String> variables) {
    private static final Pattern VARIABLE=Pattern.compile("(?i)\\b(?:roaming|variable|v|temp|t)\\.([A-Za-z_][A-Za-z0-9_]{0,63})");
    public PeerModelPolicy {actions=Set.copyOf(actions);Map<String,Set<String>> copy=new HashMap<>();controllers.forEach((key,value)->copy.put(key,Set.copyOf(value)));controllers=Map.copyOf(copy);variables=Set.copyOf(variables);}
    public static PeerModelPolicy from(RawYsmModel raw) {
        Set<String> actions=new HashSet<>(raw.properties.extraAnimations.keySet());actions.addAll(raw.properties.extraAnimations.values());raw.properties.extraAnimationClassifies.forEach(group->{actions.addAll(group.extras.keySet());actions.addAll(group.extras.values());});
        actions.removeIf(key->key==null||key.startsWith("#")||key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>256||key.chars().anyMatch(Character::isISOControl));actions.add("");Map<String,Set<String>> controllers=new HashMap<>();
        for(var file:raw.mainEntity.animationControllerFiles)file.controllers.forEach((key,value)->{Set<String> states=new HashSet<>();value.states.forEach(state->{if(state.name!=null&&state.name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=128&&!state.name.chars().anyMatch(Character::isISOControl))states.add(state.name);});states.add("");if(key!=null&&key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=128&&!key.chars().anyMatch(Character::isISOControl))controllers.put(key,states);});
        Set<String> variables=new HashSet<>();var seen=Collections.newSetFromMap(new IdentityHashMap<>());int[] count={0};
        collect(raw.mainEntity.animationFiles,variables,seen,count);collect(raw.mainEntity.animationControllerFiles,variables,seen,count);collect(raw.properties.extraAnimationButtons,variables,seen,count);
        long functionBytes=0;for(var function:raw.functionFiles.values())if(function.data!=null&&function.data.length<=65536&&(functionBytes+=function.data.length)<=1_048_576)collect(new String(function.data,java.nio.charset.StandardCharsets.UTF_8),variables,seen,count);
        return new PeerModelPolicy(actions,controllers,variables);
    }
    private static void collect(Object value,Set<String> variables,Set<Object> seen,int[] count) {
        if(value==null||++count[0]>200000)return;
        if(value instanceof String text){var matcher=VARIABLE.matcher(text);while(matcher.find()&&variables.size()<256){variables.add(matcher.group(1));variables.add(matcher.group().toLowerCase(Locale.ROOT));}return;}
        if(!seen.add(value))return;
        if(value instanceof Map<?,?> map){map.forEach((key,item)->{collect(key,variables,seen,count);collect(item,variables,seen,count);});}
        else if(value instanceof Iterable<?> items)items.forEach(item->collect(item,variables,seen,count));
        else if(value instanceof Object[] array)for(var item:array)collect(item,variables,seen,count);
        else if(value.getClass().getName().startsWith(RawYsmModel.class.getName()))for(var field:value.getClass().getFields())try{collect(field.get(value),variables,seen,count);}catch(IllegalAccessException impossible){throw new IllegalStateException(impossible);}
    }
    public boolean controller(String name,String state){var states=controllers.get(name);return states!=null&&states.contains(state);}
    public Map<String,Float> numbers(Map<String,Float> values,int limit){Map<String,Float> result=new LinkedHashMap<>();values.forEach((name,value)->{if(result.size()<limit&&variables.contains(name)&&name.matches("[A-Za-z_][A-Za-z0-9_.]{0,63}")&&!name.contains("..")&&Float.isFinite(value)&&Math.abs(value)<=1_000_000f)result.put(name,value);});return Map.copyOf(result);}
    public boolean accepts(PeerMotion motion){return actions.contains(motion.action())&&variables.containsAll(motion.roaming().keySet())&&variables.containsAll(motion.modelVariables().keySet())&&motion.events().stream().allMatch(event->variables.containsAll(event.assignments().keySet()))&&motion.controllers().entrySet().stream().allMatch(entry->controller(entry.getKey(),entry.getValue().state())&&variables.containsAll(entry.getValue().variables().keySet()));}
}
