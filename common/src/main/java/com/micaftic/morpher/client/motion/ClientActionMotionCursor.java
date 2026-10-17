package com.micaftic.morpher.client.motion;

public final class ClientActionMotionCursor {
    private String event;
    public boolean update(ClientMotion motion){if(motion==null){clear();return false;}if(motion.eventId().equals(event))return false;event=motion.eventId();return true;}
    public void clear(){event=null;}
}
