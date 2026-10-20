package com.micaftic.morpher.cloud.client;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;
import static org.junit.jupiter.api.Assertions.*;
class CloudOfflinePublicationCoordinatorTest {
    private CloudOfflineIdentityClient.Session session(){return new CloudOfflineIdentityClient.Session("coff_"+"a".repeat(32),"cos_"+"b".repeat(32),"c".repeat(43),1,1,UUID.randomUUID(),60000);}
    @Test void singleFlightActivationAndLateOldConnectionResultsCannotInstallTokens(){
        var c=new CloudOfflinePublicationCoordinator();var pending=new CompletableFuture<CloudOfflineIdentityClient.Session>();var count=new AtomicInteger();
        c.begin(0,()->{count.incrementAndGet();return pending;});c.begin(0,()->{count.incrementAndGet();return pending;});
        assertEquals(1,count.get());assertEquals(CloudOfflinePublicationCoordinator.State.ACTIVATING,c.state());
        c.reset();pending.complete(session());assertNull(c.session());assertEquals(CloudOfflinePublicationCoordinator.State.IDLE,c.state());
        var current=session();c.begin(0,()->CompletableFuture.completedFuture(current));assertSame(current,c.session());assertFalse(current.toString().contains(current.sessionToken()));
    }
    @Test void bindingReplacementStopsUntilExplicitResetAndNetworkFailureBacksOff(){
        var c=new CloudOfflinePublicationCoordinator();c.begin(0,()->CompletableFuture.failedFuture(new CloudHttpException(409,CloudErrorCode.OFFLINE_BINDING_STALE,"Replaced")));
        assertEquals(CloudOfflinePublicationCoordinator.State.STOPPED,c.state());
        var count=new AtomicInteger();c.begin(100000,()->{count.incrementAndGet();return CompletableFuture.completedFuture(session());});assertEquals(0,count.get());
        c.reset();c.begin(0,()->CompletableFuture.failedFuture(new java.io.IOException("Network")));
        c.begin(1,()->{count.incrementAndGet();return CompletableFuture.completedFuture(session());});assertEquals(0,count.get());
        c.begin(4000,()->{count.incrementAndGet();return CompletableFuture.completedFuture(session());});assertEquals(1,count.get());
    }
    @Test void pendingApprovalPollsAutomaticallyButRevokedAuthorizationStops(){
        var c=new CloudOfflinePublicationCoordinator();
        c.begin(0,()->CompletableFuture.failedFuture(new CloudHttpException(403,CloudErrorCode.OFFLINE_AUTHORIZATION_PENDING,"Pending")));
        assertEquals(CloudOfflinePublicationCoordinator.State.IDLE,c.state());
        var calls=new AtomicInteger();c.begin(14000,()->{calls.incrementAndGet();return CompletableFuture.completedFuture(session());});assertEquals(0,calls.get());
        c.begin(15000,()->{calls.incrementAndGet();return CompletableFuture.completedFuture(session());});assertEquals(1,calls.get());
        c.failed(new CloudHttpException(403,CloudErrorCode.OFFLINE_AUTHORIZATION_DENIED,"Revoked"),16000);
        assertEquals(CloudOfflinePublicationCoordinator.State.STOPPED,c.state());
        c.begin(999999,()->{calls.incrementAndGet();return CompletableFuture.completedFuture(session());});assertEquals(1,calls.get());
    }
}
