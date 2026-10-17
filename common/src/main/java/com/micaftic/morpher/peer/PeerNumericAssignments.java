package com.micaftic.morpher.peer;

import java.util.*;
import java.util.regex.Pattern;

/** Narrow local numeric option adapter. Expressions themselves are never serialized. */
public final class PeerNumericAssignments {
    private static final Pattern ASSIGN=Pattern.compile("(?i)\\s*((?:variable|v)\\.[A-Za-z_][A-Za-z0-9_]{0,48})\\s*=\\s*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:e[+-]?[0-9]+)?|true|false)\\s*;?\\s*");
    private PeerNumericAssignments(){}
    public static Map<String,Float> capture(String expression,Set<String> declared){
        if(expression==null||expression.length()>4096)return Map.of();Map<String,Float> assignments=new LinkedHashMap<>();
        for(String statement:expression.split(";")){if(statement.isBlank())continue;var match=ASSIGN.matcher(statement);if(!match.matches()||assignments.size()>=8||!declared.contains(match.group(1)))return Map.of();
            float value;try{value=match.group(2).equalsIgnoreCase("true")?1:match.group(2).equalsIgnoreCase("false")?0:Float.parseFloat(match.group(2));}catch(NumberFormatException invalid){return Map.of();}
            if(!Float.isFinite(value)||Math.abs(value)>1_000_000f)return Map.of();assignments.put(match.group(1),value);
        }
        return Map.copyOf(assignments);
    }
    public static boolean target(String name){return name!=null&&name.matches("(?i)(?:variable|v)\\.[A-Za-z_][A-Za-z0-9_]{0,48}");}
}
