package com.micaftic.morpher.peer;

/** Shared by child evaluators. Empty loops and recursive model functions are bounded too. */
public final class PeerMolangMeter {
    private boolean restricted;private long frame=Long.MIN_VALUE;private int steps,depth;
    public void begin(Object entity) {
        if(entity instanceof PeerEvaluationDomain domain) {
            restricted=domain.peerInput();long next=domain.evaluationFrame();
            if(restricted&&next!=frame){frame=next;steps=depth=0;}
        }
    }
    public void step(){if(restricted&&++steps>100000)throw new IllegalStateException("Peer Molang evaluation budget exceeded");}
    public boolean enterCall(){if(!restricted)return false;step();if(++depth>64){depth--;throw new IllegalStateException("Peer Molang call depth exceeded");}return true;}
    public void exitCall(boolean entered){if(entered)depth--;}
}
