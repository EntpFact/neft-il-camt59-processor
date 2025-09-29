package com.hdfcbank.camt59.kafkaproducer;

import com.hdfcbank.messageconnect.config.PubSubOptions;
import com.hdfcbank.messageconnect.dapr.producer.DaprProducer;
import com.hdfcbank.camt59.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class KafkaUtils {

    @Autowired
    DaprProducer daprProducer;


    public void publishToResponseTopic(String message, String topic, String msgid) {
        Map<String, String> metadata = new HashMap<>();
//        metadata.put(RAW_PAYLOAD, TRUE);  // optional, for raw XML/string
        metadata.put("partitionKey",msgid);

        var kafkaBinding = PubSubOptions.builder().requestData(message).topic(topic)
                .pubsubName(Constants.KAFKA_RESPONSE_TOPIC_DAPR_BINDING)
                .metadata(metadata)
                .build();
        var resp = daprProducer.invokeDaprPublishEvent(kafkaBinding);
        resp.doOnSuccess(res -> {
            log.info("Response published to response topic successfully");
        }).onErrorResume(res -> {
            log.info("Error on publishing the response to response topic");
            return Mono.empty();
        }).share().block();


    }
}
