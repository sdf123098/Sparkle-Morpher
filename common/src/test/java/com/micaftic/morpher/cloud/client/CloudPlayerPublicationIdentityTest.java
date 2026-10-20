package com.micaftic.morpher.cloud.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.micaftic.morpher.cloud.client.CloudPlayerPublicationIdentity.Kind.*;
class CloudPlayerPublicationIdentityTest {
    @Test void verifiedIdentityWinsAndFailedOnlineAuthenticationNeverDowngrades() {
        assertEquals(VERIFIED,CloudPlayerPublicationIdentity.choose(true,false,true,true,true));
        assertEquals(NONE,CloudPlayerPublicationIdentity.choose(false,true,true,true,true));
    }
    @Test void offlinePublicationNeedsCapabilityKeyAndAnExplicitDeclaration() {
        assertEquals(CLOUD_OFFLINE,CloudPlayerPublicationIdentity.choose(false,false,true,true,true));
        assertEquals(NONE,CloudPlayerPublicationIdentity.choose(false,false,false,true,true));
        assertEquals(NONE,CloudPlayerPublicationIdentity.choose(false,false,true,false,true));
        assertEquals(NONE,CloudPlayerPublicationIdentity.choose(false,false,true,true,false));
    }
}
