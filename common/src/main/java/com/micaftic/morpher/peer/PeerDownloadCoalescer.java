package com.micaftic.morpher.peer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** One cold byte job per root/profile. Each caller still needs its own current owner grant before apply. */
public final class PeerDownloadCoalescer implements AutoCloseable {
    private final Map<PeerModelIdentity,Group> groups=new HashMap<>();private volatile boolean closed;
    private int waiters;
    public synchronized Ticket join(PeerModelIdentity model,BooleanSupplier current)throws IOException {
        if(closed)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);
        if(waiters>=PeerLimits.CAPACITY_CEILING)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);
        Group group=groups.get(model);boolean leader=group==null;
        if(leader){if(groups.size()>=10)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);group=new Group(model);groups.put(model,group);}
        synchronized(group){if(group.waiters.size()>=PeerLimits.CAPACITY_CEILING)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);
            var ticket=new Ticket(group,current,leader);group.waiters.add(ticket);waiters++;return ticket;}
    }
    private final class Group {
        final PeerModelIdentity model;final CompletableFuture<PeerAssetStore.Verified> result=new CompletableFuture<>();final List<Ticket> waiters=new ArrayList<>();
        Group(PeerModelIdentity model){this.model=model;result.whenComplete((asset,error)->{synchronized(PeerDownloadCoalescer.this){groups.remove(model,this);}});}
        boolean current(){List<Ticket> copy;synchronized(this){copy=List.copyOf(waiters);}return copy.stream().anyMatch(ticket->!ticket.released&&ticket.current.getAsBoolean());}
    }
    public final class Ticket implements AutoCloseable {
        private final Group group;private final BooleanSupplier current;private final boolean leader;private volatile boolean released;
        Ticket(Group group,BooleanSupplier current,boolean leader){this.group=group;this.current=current;this.leader=leader;}
        public boolean leader(){return leader;}
        public boolean callerCurrent(){return !closed&&!released&&current.getAsBoolean();}
        public PeerModelIdentity model(){return group.model;}
        public boolean current(){return !closed&&group.current();}
        public CompletableFuture<PeerAssetStore.Verified> result(){return group.result;}
        public void complete(PeerAssetStore.Verified asset){group.result.complete(asset);}
        public void fail(Throwable error){group.result.completeExceptionally(error);}
        public void close(){synchronized(PeerDownloadCoalescer.this){synchronized(group){if(released)return;released=true;group.waiters.remove(this);waiters--;}}}
    }
    public synchronized int activeJobs(){return groups.size();}
    @Override public void close(){List<Group> copy;synchronized(this){closed=true;copy=List.copyOf(groups.values());groups.clear();}copy.forEach(group->group.result.completeExceptionally(new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED)));}
}
