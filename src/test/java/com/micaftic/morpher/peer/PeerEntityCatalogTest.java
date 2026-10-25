package com.micaftic.morpher.peer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PeerEntityCatalogTest {
    private PeerSession session() { return new PeerSession(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()); }
    @Test void maidsKeepSeparateModelsTexturesAndOwnerSessions() {
        var catalog=new PeerEntityCatalog(); var a=session(); var b=session(); UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        var model=new PeerModelIdentity(PeerContentRoot.digest(new byte[]{1}),"ysm-v1");
        var first=new PeerEntityAppearance(a,one,1,model,"blue",false);
        assertTrue(catalog.publish(first)); assertTrue(catalog.publish(new PeerEntityAppearance(a,two,2,null,"xiduo",false)));
        assertTrue(catalog.publish(new PeerEntityAppearance(b,one,1,null,"other",false)));
        assertEquals(first,catalog.get(a,one)); assertEquals("xiduo",catalog.get(a,two).texture());
        assertNull(catalog.get(a,two).model()); assertEquals(3,catalog.page(0).entries().size());
        catalog.leave(b); assertEquals(2,catalog.page(0).entries().size());
    }
    @Test void withdrawnOrChangedAppearanceCannotBeReplayedAndDoesNotAuthorizeOldBytes() {
        var catalog=new PeerEntityCatalog();var a=session();UUID id=UUID.randomUUID();
        var entry=new PeerEntityAppearance(a,id,1,new PeerModelIdentity(PeerContentRoot.digest(new byte[]{1}),"ysm-v1"),"red",false);
        catalog.publish(entry);assertTrue(catalog.owns(a,entry.assetAppearance()));
        assertTrue(catalog.publish(new PeerEntityAppearance(a,id,2,null,null,true)));
        assertFalse(catalog.publish(entry));assertFalse(catalog.owns(a,entry.assetAppearance()));assertNull(catalog.get(a,id));
    }
    @Test void catalogIsBoundedWithoutLettingOneOwnerOccupyAnotherOwnersSlots() {
        var catalog=new PeerEntityCatalog();var a=session();var b=session();
        for(int i=0;i<PeerEntityCatalog.PER_OWNER;i++)catalog.publish(new PeerEntityAppearance(a,UUID.randomUUID(),i+1,null,"default",false));
        assertThrows(IllegalStateException.class,()->catalog.publish(new PeerEntityAppearance(a,UUID.randomUUID(),99,null,"default",false)));
        assertTrue(catalog.publish(new PeerEntityAppearance(b,UUID.randomUUID(),1,null,"default",false)));
    }
}
