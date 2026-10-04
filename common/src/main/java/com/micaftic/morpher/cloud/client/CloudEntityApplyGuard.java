package com.micaftic.morpher.cloud.client;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-entity lifetime tokens. Same UUID does not mean the same loaded entity instance. */
public final class CloudEntityApplyGuard {
    public record Token(CloudEntityPresenceClient.Entry entry, Object entity, long generation) {}
    private final Map<UUID, Token> active = new HashMap<>();
    public Token begin(CloudEntityPresenceClient.Entry entry, Object entity, long generation) {
        var token = new Token(entry, entity, generation);
        active.put(entry.entityId(), token);
        return token;
    }
    public boolean accept(Token token, CloudEntityPresenceClient.Entry currentEntry, Object currentEntity,
                          long generation, boolean privacy, boolean verifiedPlayer) {
        return token != null && active.get(token.entry().entityId()) == token && token.generation() == generation
                && token.entity() == currentEntity && token.entity() != null && token.entry().equals(currentEntry)
                && !privacy && !verifiedPlayer;
    }
    public void remove(UUID id) { active.remove(id); }
    public void clear() { active.clear(); }
}
