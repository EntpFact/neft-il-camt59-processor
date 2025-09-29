package com.hdfcbank.camt59.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.camt59.kafkaproducer.KafkaUtils;
import com.hdfcbank.camt59.model.Header;
import com.hdfcbank.camt59.model.ReqPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.*;

class ErrorHandlingTest {

    @Mock
    private KafkaUtils kafkaUtils;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ErrorHandling errorHandling;

    private ReqPayload reqPayload;
    private Header header;

    private final String errortopic = "error-topic";
    private final String dispatchertopic = "dispatcher-topic";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Inject values for @Value fields
        errorHandling = new ErrorHandling();
        errorHandling.kafkaUtils = kafkaUtils;
        errorHandling.objectMapper = objectMapper;

        // simulate @Value injection
        try {
            java.lang.reflect.Field errorTopicField = ErrorHandling.class.getDeclaredField("errortopic");
            errorTopicField.setAccessible(true);
            errorTopicField.set(errorHandling, errortopic);

            java.lang.reflect.Field dispatcherTopicField = ErrorHandling.class.getDeclaredField("dispatchertopic");
            dispatcherTopicField.setAccessible(true);
            dispatcherTopicField.set(errorHandling, dispatchertopic);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        header = new Header();
        header.setMsgId("msg1234");
        reqPayload = new ReqPayload();
        reqPayload.setHeader(header);
    }

    @Test
    void testHandleInvalidPayload_Success() throws Exception {

        String request = "{\"header\":{\"target\":\"error-topic\" ,\"msgId\":\"msg123\"}}";
        String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
//        String serializedPayload = "{\"data_base64\":\"" + base64 + "\"}";

        when(objectMapper.writeValueAsString(reqPayload)).thenReturn(request);

        errorHandling.handleInvalidPayload(reqPayload);

        // Verify header target is set to errortopic
        assert reqPayload.getHeader().getTarget().equals(errortopic);

        // Verify ObjectMapper is called
        verify(objectMapper, times(1)).writeValueAsString(reqPayload);

        // Verify Kafka publish is called
        verify(kafkaUtils, times(1)).publishToResponseTopic(request, dispatchertopic,"msg1234");
    }

    @Test
    void testHandleInvalidPayload_ThrowsJsonProcessingException() throws Exception {
        when(objectMapper.writeValueAsString(reqPayload)).thenThrow(new JsonProcessingException("Serialization failed") {});

        try {
            errorHandling.handleInvalidPayload(reqPayload);
        } catch (JsonProcessingException ex) {
            // Expected
        }

        // Verify KafkaUtils not called
        verify(kafkaUtils, never()).publishToResponseTopic(anyString(), anyString(),anyString());
    }
}
