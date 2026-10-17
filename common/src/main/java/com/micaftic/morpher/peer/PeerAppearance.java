package com.micaftic.morpher.peer;


/** Clear/default is revisioned too. Resource availability and rendering are separate observer states. */
public record PeerAppearance(long revision,PeerModelIdentity model,String textureVariant) {
    public PeerAppearance {
        if(revision<1)throw new IllegalArgumentException("Invalid Peer appearance revision");
        if(model==null)textureVariant=null;
        else if(textureVariant==null || textureVariant.isBlank() || textureVariant.length()>256
                || textureVariant.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid Peer texture variant");
    }
    public boolean cleared(){return model==null;}
}
