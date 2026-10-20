package com.micaftic.morpher.cloud.client;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

/** One connection owns one publication lease. A replaced binding never triggers an automatic takeover. */
public final class CloudOfflinePublicationCoordinator {
    public enum State { IDLE, ACTIVATING, ACTIVE, STOPPED }
    private long generation,nextAttempt;
    private State state=State.IDLE;
    private CloudOfflineIdentityClient.Session session;
    private CloudErrorCode error=CloudErrorCode.NONE;
    public synchronized State state(){return state;}
    public synchronized CloudErrorCode error(){return error;}
    public synchronized CloudOfflineIdentityClient.Session session(){return session;}
    public synchronized void reset(){generation++;state=State.IDLE;session=null;nextAttempt=0;error=CloudErrorCode.NONE;}
    public synchronized void begin(long now,Supplier<CompletableFuture<CloudOfflineIdentityClient.Session>> action){
        if(state!=State.IDLE||now<nextAttempt)return;
        state=State.ACTIVATING;long token=generation;
        CompletableFuture<CloudOfflineIdentityClient.Session> operation;
        try{operation=Objects.requireNonNull(action.get());}catch(RuntimeException failure){failed(failure,now);return;}
        operation.whenComplete((result,failure)->{synchronized(this){
            if(token!=generation)return;
            if(failure==null){session=Objects.requireNonNull(result);state=State.ACTIVE;error=CloudErrorCode.NONE;}
            else failed(failure,now);
        }});
    }
    public synchronized void acknowledge(CloudOfflineIdentityClient.Session expected,long revision){if(session==expected)session=session.withRevision(revision);}
    public synchronized void failed(Throwable failure,long now){
        while(failure instanceof CompletionException&&failure.getCause()!=null)failure=failure.getCause();
        error=failure instanceof CloudHttpException http?http.errorCode():CloudErrorCode.INTERNAL;
        if(error==CloudErrorCode.OFFLINE_AUTHORIZATION_PENDING){state=State.IDLE;session=null;nextAttempt=now+15000;return;}
        if(failure instanceof CloudHttpException http&&(http.statusCode()==401||http.statusCode()==403||http.statusCode()==409)){
            state=State.STOPPED;session=null;
        }else{state=session==null?State.IDLE:State.ACTIVE;nextAttempt=now+3000;}
    }
}
