package com.hdfcbank.camt59.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.camt59.kafkaproducer.KafkaUtils;
import com.hdfcbank.camt59.model.ReqPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ErrorHandling {


    @Autowired
    KafkaUtils kafkaUtils;

    @Value("${topic.errortopic}")
    private String errortopic;

    @Value("${topic.dispatchertopic}")
    private String dispatchertopic;

    @Autowired
    ObjectMapper objectMapper;

    public void handleInvalidPayload(ReqPayload reqPayload) throws JsonProcessingException {
        reqPayload.getHeader().setTarget(errortopic);
        String reqPayloadString = objectMapper.writeValueAsString(reqPayload);
        kafkaUtils.publishToResponseTopic(reqPayloadString,dispatchertopic,reqPayload.getHeader().getMsgId());

    }
}