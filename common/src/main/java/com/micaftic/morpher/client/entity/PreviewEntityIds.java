package com.micaftic.morpher.client.entity;

import java.util.concurrent.atomic.AtomicInteger;

/** Private negative IDs: preview entities are never registered in the client world. */
final class PreviewEntityIds {
    private static final AtomicInteger NEXT=new AtomicInteger(-1);
    private PreviewEntityIds() {}
    static int next(){return NEXT.getAndUpdate(id->id==Integer.MIN_VALUE?-1:id-1);}
}
