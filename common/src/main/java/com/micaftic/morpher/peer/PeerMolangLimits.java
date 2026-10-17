package com.micaftic.morpher.peer;

import java.io.*;

/** Peer-only compiler input limits. Source may contain model scripts, network motion never contains scripts. */
public final class PeerMolangLimits {
    private PeerMolangLimits(){}
    public static String read(Reader reader)throws IOException {
        StringBuilder text=new StringBuilder();char[] chars=new char[4096];int count;
        while((count=reader.read(chars))!=-1){if(text.length()+count>65536)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);text.append(chars,0,count);}
        syntax(text.toString());return text.toString();
    }
    public static void syntax(String text)throws IOException {
        if(text.length()>65536)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        int depth=0,operators=0,tokens=0;boolean quote=false,line=false,block=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i),next=i+1<text.length()?text.charAt(i+1):0;
            if(line){if(c=='\n'||c=='\r')line=false;continue;}
            if(block){if(c=='*'&&next=='/'){block=false;i++;}continue;}
            if(c=='\''){quote=!quote;continue;}if(quote)continue;
            if(c=='/'&&next=='/'){line=true;i++;continue;}if(c=='/'&&next=='*'){block=true;i++;continue;}
            if(!Character.isWhitespace(c)&&++tokens>16384)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
            if(c=='('||c=='['||c=='{'){if(++depth>32)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);}
            if(c==')'||c==']'||c=='}')depth--;
            if(c==';')operators=0;
            else if("+-*/?=:.,&|<>".indexOf(c)>=0&&++operators>64)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        }
    }
}
