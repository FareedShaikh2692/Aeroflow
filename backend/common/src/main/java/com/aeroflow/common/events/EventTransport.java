package com.aeroflow.common.events;

/** Delivers an already-serialised event envelope to the shared event stream. */
public interface EventTransport {
    void send(String topic, String key, String payload) throws Exception;
}
