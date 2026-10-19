package com.micaftic.morpher.client.animation.molang;

import java.util.function.IntSupplier;

/** Entity identity used by diagnostics, including previews that never enter a world. */
public final class EntityLogIdentity {
    private EntityLogIdentity() {}
    public static String describe(IntSupplier id){
        try {int value=id.getAsInt();return value==0?"unassigned":Integer.toString(value);}
        catch(RuntimeException unavailable){return "unassigned";}
    }
}
