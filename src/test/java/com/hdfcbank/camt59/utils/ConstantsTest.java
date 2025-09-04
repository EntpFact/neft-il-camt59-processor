package com.hdfcbank.camt59.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void testKafkaResponseTopicDaprBinding() {
        assertEquals("kafka-nilrouter-pubsub-component", Constants.KAFKA_RESPONSE_TOPIC_DAPR_BINDING);
    }

    @Test
    void testNilConstant() {
        assertEquals("NIL", Constants.NIL);
    }

    @Test
    void testInwardConstant() {
        assertEquals("Inward", Constants.INWARD);
    }

    @Test
    void testOutwardConstant() {
        assertEquals("Outward", Constants.OUTWARD);
    }

    @Test
    void testFcConstant() {
        assertEquals("FC", Constants.FC);
    }

    @Test
    void testEphConstant() {
        assertEquals("EPH", Constants.EPH);
    }

    @Test
    void testReceivedConstant() {
        assertEquals("RECEIVED", Constants.RECEIVED);
    }

    @Test
    void testSentToDispatcherConstant() {
        assertEquals("SENT_TO_DISPATCHER", Constants.SENT_TO_DISPATCHER);
    }
}
