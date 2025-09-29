package com.hdfcbank.camt59.kafkaproducer;

import com.hdfcbank.messageconnect.config.PubSubOptions;
import com.hdfcbank.messageconnect.dapr.producer.DaprProducer;
import com.hdfcbank.camt59.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;


class KafkaUtilsTest {

    @InjectMocks
    private KafkaUtils kafkaUtils;

    @Mock
    private DaprProducer daprProducer;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPublishToResponseTopicSuccess() {
        String message = "Test Message";
        String topic = "test-topic";

        when(daprProducer.invokeDaprPublishEvent(any(PubSubOptions.class)))
                .thenReturn(Mono.just("Published"));

        kafkaUtils.publishToResponseTopic(message, topic,"msg123");

//        verify(daprProducer, times(1)).invokeDaprPublishEvent(argThat(options ->
//                options.getRequestData().equals(message) &&
//                        options.getTopic().equals(topic) &&
//                        options.getPubsubName().equals(Constants.KAFKA_RESPONSE_TOPIC_DAPR_BINDING)
//        ));
    }

    @Test
    void testPublishToResponseTopicError() {
        String message = "Test Message";
        String topic = "test-topic";

        when(daprProducer.invokeDaprPublishEvent(any(PubSubOptions.class)))
                .thenReturn(Mono.error(new RuntimeException("Kafka error")));

        kafkaUtils.publishToResponseTopic(message, topic,"ms1234");

//        verify(daprProducer, times(1)).invokeDaprPublishEvent(any(PubSubOptions.class));
    }
}
