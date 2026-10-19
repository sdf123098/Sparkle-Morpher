package com.micaftic.morpher.client.renderer.preview;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StaticPreviewCacheTest {
    @Test void unchangedCardIsRenderedOnceWhileOtherPreviewsRemainLive(){
        var cache=new StaticPreviewCache();Object card="screen1/slot0/modelA/texA/size81x106";int draws=0;
        for(int frame=0;frame<600;frame++){if(!cache.canReuse(card))draws++;cache.rendered(card);}
        assertEquals(1,draws);
        assertFalse(cache.canReuse("screen1/slot0/modelA/texB/size81x106"));
        assertFalse(cache.canReuse("screen1/slot0/modelB/texA/size81x106"));
        assertFalse(cache.canReuse("screen2/slot0/modelA/texA/size81x106"));
        assertFalse(cache.canReuse("screen1/slot0/modelA/texA/size50x66"));
        assertFalse(cache.canReuse(null));cache.rendered(null);assertFalse(cache.canReuse(card));
    }
}
