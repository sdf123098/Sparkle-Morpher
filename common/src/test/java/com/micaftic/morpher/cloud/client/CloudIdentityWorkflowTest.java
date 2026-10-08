package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class CloudIdentityWorkflowTest {
    static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
    static final CloudScopeClient.CloudScope SCOPE = new CloudScopeClient.CloudScope("world", "tenant", "World", "epoch", "STRICT_APPROVAL");
    static CloudIdentityClient.CloudIdentity identity(String scope) { return new CloudIdentityClient.CloudIdentity("identity", "me", "offline:"+scope+":"+PLAYER, "Player", "UNVERIFIED"); }
    static CloudIdentityBindingClient.CloudBinding binding(String scope, String epoch, String status, long revision) {
        return new CloudIdentityBindingClient.CloudBinding("binding", "me", "identity", "target", scope, epoch, PLAYER.toString(), "STRICT_APPROVAL", status, null, revision);
    }
    static final class Fake implements CloudIdentityWorkflow.Gateway {
        String role="editor", policy="STRICT_APPROVAL";
        List<CloudIdentityClient.CloudIdentity> identities=List.of(identity("world"));
        List<CloudIdentityBindingClient.CloudBinding> bindings=List.of();
        boolean valid=true;
        int requests, approvals, issues;
        long approvedRevision;
        String approvedStatus;
        CompletableFuture<CloudIdentityWorkflow.Catalog> pendingLoad;
        CompletableFuture<CloudIdentityBindingClient.CloudBinding> pendingWrite;
        public CloudIdentityWorkflow.Context context(){return new CloudIdentityWorkflow.Context("me", SCOPE, PLAYER, "Player");}
        public void check(){if(!valid)throw new java.util.concurrent.CancellationException();}
        public CompletableFuture<CloudIdentityWorkflow.Catalog> load(){ return pendingLoad != null ? pendingLoad : CompletableFuture.completedFuture(data()); }
        CloudIdentityWorkflow.Catalog data(){return new CloudIdentityWorkflow.Catalog(List.of(new CloudIdentityClient.IdentityProvider("official","Minecraft")),identities,
                List.of(new CloudScopeClient.CloudTarget("target","world","PLAYER","Character",0),new CloudScopeClient.CloudTarget("wrong","other","PLAYER","Other",0),new CloudScopeClient.CloudTarget("maid","world","MAID","Maid",0)), bindings,
                new CloudScopeClient.CloudScopePermissions("world",role,policy),List.of(new CloudIdentityWorkflow.Player(PLAYER,"Player")));}
        public CompletableFuture<CloudIdentityClient.CloudIdentity> verify(String provider){return CompletableFuture.completedFuture(identity("world"));}
        public CompletableFuture<CloudIdentityClient.CloudIdentity> register(){identities=List.of(identity("world"));return CompletableFuture.completedFuture(identities.get(0));}
        public CompletableFuture<CloudIdentityBindingClient.CloudBinding> request(String id,String target){requests++; assertEquals("identity",id);assertEquals("target",target);return pendingWrite!=null?pendingWrite:CompletableFuture.completedFuture(binding("world","epoch","PENDING_APPROVAL",0));}
        public CompletableFuture<CloudIdentityBindingClient.CloudBinding> redeem(String code,String id){return CompletableFuture.completedFuture(binding("world","epoch","APPROVED",1));}
        public CompletableFuture<CloudIdentityBindingClient.CloudClaimCode> issue(String target,UUID player){issues++;return CompletableFuture.completedFuture(new CloudIdentityBindingClient.CloudClaimCode("secret","world","epoch",target,player.toString(),600));}
        public CompletableFuture<Void> revoke(String code){return CompletableFuture.completedFuture(null);}
        public CompletableFuture<CloudIdentityBindingClient.CloudBinding> approve(String id,String status,long revision){approvals++;approvedRevision=revision;approvedStatus=status;return CompletableFuture.completedFuture(binding("world","epoch",status,revision+1));}
    }
    @Test void scopesRolesAndPoliciesControlActionsWithoutElevatingOfflineIdentity(){
        var fake=new Fake();var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();
        assertEquals(1,flow.catalog().targets().size());assertTrue(flow.canRequest());assertFalse(flow.canManage());
        flow.approve(true);flow.issue();assertEquals(0,fake.approvals);assertEquals(0,fake.issues);
        fake.role="viewer";flow.refresh();assertFalse(flow.canRegister());assertFalse(flow.canRequest());
        fake.role="manage";fake.policy="DISABLED";fake.identities=List.of();flow.refresh();
        assertTrue(flow.canManage());assertFalse(flow.canRegister());assertFalse(flow.canRequest());assertFalse(flow.canIssue());
        fake.policy="CLAIM_CODE";fake.identities=List.of(identity("other"));flow.refresh();assertNull(flow.localOfflineIdentity());assertFalse(flow.canRedeem());
        fake.identities=List.of(identity("world"));flow.refresh();flow.codeDraft("  secret  ");assertTrue(flow.canRedeem());assertFalse(flow.canRequest());
    }
    @Test void pendingAndApprovedBindingsPreventDuplicateApplicationsAndIgnoreOtherEpochs(){
        var fake=new Fake();fake.bindings=List.of(binding("world","old","APPROVED",10));
        var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();assertTrue(flow.canRequest());
        fake.bindings=List.of(binding("world","epoch","PENDING_APPROVAL",0));flow.refresh();assertFalse(flow.canRequest());
        fake.bindings=List.of(binding("world","epoch","APPROVED",1));flow.refresh();assertFalse(flow.canRequest());
    }
    @Test void submittedOperationIsSingleFlightAndCloseOrContextChangeDiscardsCompletion(){
        var fake=new Fake();fake.pendingWrite=new CompletableFuture<>();
        var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();flow.request();flow.request();assertEquals(1,fake.requests);assertTrue(flow.busy());
        flow.invalidate();fake.pendingWrite.complete(binding("world","epoch","PENDING_APPROVAL",0));assertFalse(flow.loaded());assertEquals("",flow.codeDraft());
        var queued=new ArrayDeque<Runnable>();fake.pendingLoad=new CompletableFuture<>();
        var next=new CloudIdentityWorkflow(fake,queued::add);next.refresh();fake.pendingLoad.complete(fake.data());fake.valid=false;
        queued.remove().run();assertFalse(next.loaded());assertFalse(next.canManage());
    }
    @Test void approvalUsesSelectedServerRevisionAndCanRejectInsteadOfGranting(){
        var fake=new Fake();fake.role="manage";fake.bindings=List.of(binding("world","epoch","PENDING_APPROVAL",7));
        var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();assertTrue(flow.canApprove());flow.approve(false);
        assertEquals(1,fake.approvals);assertEquals(7,fake.approvedRevision);assertEquals("REJECTED",fake.approvedStatus);
    }
    @Test void oneLiveInvitationMustBeRevokedBeforeAnotherIsIssuedAndIsClearedOnClose(){
        var fake=new Fake();fake.role="manage";fake.policy="CLAIM_CODE";
        var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();flow.issue();flow.issue();assertEquals(1,fake.issues);
        assertNotNull(flow.issued());flow.revoke();assertNull(flow.issued());assertTrue(flow.canIssue());flow.issue();flow.invalidate();assertNull(flow.issued());
    }
    @Test void malformedPermissionsFailClosed(){
        assertThrows(IllegalArgumentException.class,()->new CloudScopeClient.CloudScopePermissions("world","admin","CLAIM_CODE"));
        assertThrows(IllegalArgumentException.class,()->new CloudScopeClient.CloudScopePermissions("world","editor","unknown"));
        var fake=new Fake();fake.pendingLoad=CompletableFuture.completedFuture(new CloudIdentityWorkflow.Catalog(List.of(),List.of(),List.of(),List.of(),new CloudScopeClient.CloudScopePermissions("other","manage","STRICT_APPROVAL"),List.of()));
        var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();assertFalse(flow.loaded());assertFalse(flow.canManage());
    }
    @Test void olderServerShowsReadOnlyIdentityInsteadOfEnablingWorldWrites(){
        var fake=new Fake();var data=fake.data();
        fake.pendingLoad=CompletableFuture.completedFuture(new CloudIdentityWorkflow.Catalog(data.providers(),data.identities(),data.targets(),data.bindings(),null,data.players()));
        var flow=new CloudIdentityWorkflow(fake,Runnable::run);flow.refresh();
        assertTrue(flow.loaded());assertEquals("server_update",flow.message());
        assertFalse(flow.canManage());assertFalse(flow.canRegister());assertFalse(flow.canRequest());assertFalse(flow.canRedeem());assertFalse(flow.canIssue());
        assertEquals(1,flow.catalog().identities().size());
    }
}
