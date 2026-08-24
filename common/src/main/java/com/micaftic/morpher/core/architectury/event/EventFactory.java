package com.micaftic.morpher.core.architectury.event;

/**
 * Stub for com.micaftic.morpher.core.architectury.event.EventFactory.
 */
public class EventFactory {
    public static <T> Event<T> createEventResult() {
        return new Event<>();
    }
}
