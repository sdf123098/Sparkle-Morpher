package com.micaftic.morpher.peer;

import com.micaftic.morpher.core.model.ModelRef;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PeerContractsTest {
    private static PeerAssetManifest.Resource model(String path, String data) {
        byte[] bytes=data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new PeerAssetManifest.Resource(path,PeerAssetManifest.Role.MODEL,bytes.length,PeerContentRoot.digest(bytes));
    }
    @Test void sameNameDifferentBytesAndProfilesDoNotAlias() {
        var a=new PeerAssetManifest(1,"model.ysm",List.of(model("model.ysm","a")));
        var b=new PeerAssetManifest(1,"model.ysm",List.of(model("model.ysm","b")));
        assertEquals("d4258f37e6c8e2cf30744f7679f7e48816a85985a2863b05a20653d3416b4735",a.contentRoot().sha256());
        assertNotEquals(a.contentRoot(),b.contentRoot());
        assertNotEquals(new PeerModelIdentity(a.contentRoot(),"ysm-v1"),new PeerModelIdentity(a.contentRoot(),"gltf-v1"));
        var ref=new PeerModelIdentity(a.contentRoot(),"ysm-v1").modelRef();
        assertEquals(ref,ModelRef.parse(ref.toString()));
        for(String legacy:List.of("local:cirno","builtin:default","server:cirno","server-forced:x","cloud:instance:asset"))
            assertEquals(legacy,ModelRef.parse(legacy).toString());
    }
    @Test void canonicalIdentityIsIndependentOfResourceOrder() {
        var a=model("model.ysm","a"); var b=model("another.ysm","b");
        assertArrayEquals(new PeerAssetManifest(1,"model.ysm",List.of(a,b)).canonicalBytes(),
                new PeerAssetManifest(1,"model.ysm",List.of(b,a)).canonicalBytes());
        assertNotEquals(new PeerAssetManifest(1,"model.ysm",List.of(a,b)).contentRoot(),
                new PeerAssetManifest(1,"another.ysm",List.of(a,b)).contentRoot());
    }
    @Test void refusesUnsafePathsAndPlatformAliases() {
        for(String path:List.of("../a","/a","a/../b","C:/a","a:b","\\\\host\\a","a//b","a/./b","CON.txt","a/NUL","a.","a ","e\u0301.ysm","x\u0000y","\uD800"))
            assertThrows(IllegalArgumentException.class,()->PeerPaths.validate(path),path);
        assertThrows(IllegalArgumentException.class,()->new PeerAssetManifest(1,"A.ysm",List.of(model("A.ysm","a"),model("a.ysm","b"))));
        assertThrows(IllegalArgumentException.class,()->new PeerAssetManifest(1,"a",List.of(model("a","a"),model("a","b"))));
    }
    @Test void requiresFullShaAndValidEntrypointAndBudget() {
        assertThrows(IllegalArgumentException.class,()->new PeerContentRoot("deadbeef"));
        assertThrows(IllegalArgumentException.class,()->new PeerContentRoot("A".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->new PeerModelIdentity(PeerContentRoot.digest(new byte[0]),"unknown-v1"));
        assertThrows(IllegalArgumentException.class,()->new PeerAssetManifest(1,"missing",List.of(model("a","a"))));
        assertThrows(IllegalArgumentException.class,()->new PeerAssetManifest.Resource("a",PeerAssetManifest.Role.MODEL,Long.MAX_VALUE,PeerContentRoot.digest(new byte[0])));
    }
    @Test void cloudAuthAndUnknownNeverBecomeShareableByCopyingPathsOrLegacyFlags() {
        for(var kind:ModelOriginRecord.SourceKind.values()) {
            var r=new ModelOriginRecord(kind,Path.of("custom/copied.ysm"),PeerContentRoot.digest(new byte[0]),UUID.randomUUID(),
                    ModelOriginRecord.Permission.USER_APPROVED,"ysm-v1","ysm",List.of("copied.ysm"),true);
            assertEquals(kind==ModelOriginRecord.SourceKind.CUSTOM || kind==ModelOriginRecord.SourceKind.BUILTIN,r.allowsLocalShare());
            assertFalse(r.allowsRoomRelay());
            assertFalse(r.toString().contains("copied"));
        }
    }
    @Test void reconnectIdentityIsNotJustRoomOrPlayerUuid() {
        UUID room=UUID.randomUUID(),epoch=UUID.randomUUID(),member=UUID.randomUUID(),player=UUID.randomUUID();
        assertNotEquals(new PeerSession(room,epoch,member,UUID.randomUUID(),player),new PeerSession(room,epoch,member,UUID.randomUUID(),player));
    }
    @Test void missingOrUnknownPolicyPreservesCloudAndDisablesPeer() {
        for(String value:new String[]{null,"","unknown","peer_room"}) {
            var mode=ConnectivityPolicy.fromConfig(value);
            assertTrue(mode.allowsCloud(false)); assertFalse(mode.allowsPeer(false));
        }
        assertFalse(ConnectivityPolicy.PEER_ROOM.allowsCloud(false));
        assertTrue(ConnectivityPolicy.PEER_ROOM.allowsPeer(false));
        for(var mode:ConnectivityPolicy.values()) { assertFalse(mode.allowsPeer(true)); assertFalse(mode.allowsCloud(true)); }
    }
}
