package com.micaftic.morpher.client.motion;

public final class ClientControllerMotionCursor {
    private ClientMotion.Controller last;
    public boolean needsTransition(ClientMotion.Controller next){return last==null||!last.state().equals(next.state())||!last.eventId().equals(next.eventId());}
    public boolean needsVariables(ClientMotion.Controller next){return needsTransition(next)||!last.variables().equals(next.variables());}
    public void accept(ClientMotion.Controller next){last=next;}
    public void clear(){last=null;}
}
