package com.micaftic.morpher.cloud.client;

import java.util.*;

/** Explicit firing inputs, never a search for the nearest shooter or their later equipment. */
public final class ProjectileFiringCandidates {
    public enum Family { ARROW, TRIDENT, SNOWBALL, EGG, FIREWORK }
    public record Firing(UUID ownerUuid, String eventId, Set<Family> families, int projectileCount,
                         String runtimeModelId, CloudPlayerSelection selection, Map<String,Float> variables,
                         String firingItemId, double x, double y, double z, double dx, double dy, double dz) {
        public Firing {
            Objects.requireNonNull(ownerUuid); EntityDisplayContext.slug(eventId);
            families=Set.copyOf(families);variables=Map.copyOf(variables);
            if(families.isEmpty()||projectileCount<1||projectileCount>16||runtimeModelId==null||runtimeModelId.isBlank()
                    ||variables.size()>32||variables.entrySet().stream().anyMatch(e->e.getKey().isBlank()||e.getKey().length()>32
                    ||e.getKey().chars().anyMatch(Character::isISOControl)||!Float.isFinite(e.getValue()))
                    ||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz)
                    ||Math.abs(dx*dx+dy*dy+dz*dz-1)>0.01)throw new IllegalArgumentException("Invalid firing candidate");
            if(selection!=null)selection=selection.withoutMotion();
            if(firingItemId!=null)EntityDisplayContext.identifier(firingItemId);
        }
    }
    private static final long WINDOW=1_500_000_000L;
    private record Pending(Firing firing,long deadline,int remaining) {}
    private final List<Pending> pending=new ArrayList<>();
    private final Set<UUID> seen=new HashSet<>();
    private final Map<UUID,Long> ambiguous=new HashMap<>();
    private EntityDisplayContext context;
    private boolean saturated;
    public void enter(EntityDisplayContext next){if(!Objects.equals(context,next)){context=next;pending.clear();seen.clear();ambiguous.clear();saturated=false;}}
    public boolean offer(EntityDisplayContext expected,Firing firing,long now){
        expire(now);if(context==null||!context.equals(expected)||saturated||pending.size()>=64||ambiguous.containsKey(firing.ownerUuid()))return false;
        if(pending.stream().anyMatch(old->old.firing.ownerUuid().equals(firing.ownerUuid())&&!Collections.disjoint(old.firing.families(),firing.families()))){
            pending.removeIf(old->old.firing.ownerUuid().equals(firing.ownerUuid()));ambiguous.put(firing.ownerUuid(),now+WINDOW);return false;
        }
        pending.add(new Pending(firing,now+WINDOW,firing.projectileCount()));return true;
    }
    /** Called only for a newly received native spawn, with its native owner and initial trajectory. */
    public Firing match(EntityDisplayContext expected,UUID projectile,UUID nativeOwner,Family family,
                        double x,double y,double z,double vx,double vy,double vz,long now){
        expire(now);if(context==null||!context.equals(expected)||saturated)return null;
        if(seen.contains(projectile))return null;
        if(seen.size()>=65536){saturated=true;pending.clear();return null;}seen.add(projectile);
        if(nativeOwner==null||ambiguous.containsKey(nativeOwner))return null;
        double speed=Math.sqrt(vx*vx+vy*vy+vz*vz);if(!Double.isFinite(speed)||speed<0.1)return null;
        List<Pending> choices=pending.stream().filter(p->p.firing.ownerUuid().equals(nativeOwner)&&p.firing.families().contains(family)).toList();
        if(choices.size()!=1)return null;Pending choice=choices.get(0);Firing firing=choice.firing;
        // The owner comes from the native packet; geometry only rejects incompatible firing events.
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||square(x-firing.x)+square(y-firing.y)+square(z-firing.z)>4
                ||(vx*firing.dx+vy*firing.dy+vz*firing.dz)/speed<0.85)return null;
        pending.remove(choice);if(choice.remaining>1)pending.add(new Pending(firing,choice.deadline,choice.remaining-1));
        return firing;
    }
    public void remember(UUID projectile){if(seen.size()<65536)seen.add(projectile);else{saturated=true;pending.clear();}}
    private void expire(long now){pending.removeIf(p->now-p.deadline>=0);ambiguous.entrySet().removeIf(e->now-e.getValue()>=0);}
    private static double square(double value){return value*value;}
}
