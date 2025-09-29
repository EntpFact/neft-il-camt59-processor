package com.hdfcbank.camt59.controller;

import com.hdfcbank.camt59.exception.NILException;
import com.hdfcbank.camt59.model.ReqPayload;
import com.hdfcbank.camt59.model.Response;
import com.hdfcbank.camt59.service.Camt59XmlProcessor;
import com.hdfcbank.camt59.utils.NILRouterCommonUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessControllerTest {

    @InjectMocks
    private ProcessController processController;

    @Mock
    private Camt59XmlProcessor camt59XmlProcessor;

    @Mock
    private NILRouterCommonUtility nilRouterCommonUtility;


    private ReqPayload mockPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockPayload = new ReqPayload();
    }
    @Test
    void testHealthz() {
        ResponseEntity<?> response = processController.healthz();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Success", response.getBody());
    }

    @Test
    void testReady() {
        ResponseEntity<?> response = processController.ready();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Success", response.getBody());
    }

    @Test
    void testProcess_Success() throws Exception {
        String request = "{ \"sample\": \"data\" }";
        String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
        String requestJson = "{\"data_base64\":\"" + base64 + "\"}";

        when(nilRouterCommonUtility.convertToMap(request)).thenReturn(mockPayload);
        when(camt59XmlProcessor.validateRequest(any(ReqPayload.class))).thenReturn(false);
        doNothing().when(camt59XmlProcessor).processXML(any(ReqPayload.class));

        Mono<ResponseEntity<Response>> result = processController.process(request);


//        verify(camt59XmlProcessor, times(1)).processXML(any(ReqPayload.class));
    }

    void testProcess_Success_WhenValidationFailsAndProcessCalled() throws Exception {
        String request = "{ \"sample\": \"data\" }";
        String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
        String requestJson = "{\"data_base64\":\"" + base64 + "\"}";

        when(nilRouterCommonUtility.convertToMap(requestJson)).thenReturn(mockPayload);
        when(camt59XmlProcessor.validateRequest(mockPayload)).thenReturn(false);
        doNothing().when(camt59XmlProcessor).processXML(mockPayload);

        Mono<ResponseEntity<Response>> result = processController.process(requestJson);

        StepVerifier.create(result)
                .assertNext(response -> {
//                    assertEquals(200, response.getStatusCode().value());
                    assertEquals("SUCCESS", response.getBody().getStatus());
                    assertEquals("Message Processed.", response.getBody().getMessage());
                })
                .verifyComplete();

        verify(camt59XmlProcessor, times(1)).processXML(mockPayload);
    }

    @Test
    void testProcess_Success_WhenValidationTrueAndProcessSkipped() throws Exception {
        String request = "{ \"sample\": \"data\" }";
        String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
        String requestJson = "{\"data_base64\":\"" + base64 + "\"}";

        when(nilRouterCommonUtility.convertToMap(requestJson)).thenReturn(mockPayload);
        when(camt59XmlProcessor.validateRequest(mockPayload)).thenReturn(true);

        Mono<ResponseEntity<Response>> result = processController.process(requestJson);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCodeValue());
                    assertEquals("SUCCESS", response.getBody().getStatus());
                    assertEquals("Message Processed.", response.getBody().getMessage());
                })
                .verifyComplete();

        verify(camt59XmlProcessor, never()).processXML(any());
    }

    @Test
    void testProcess_Error_WhenProcessXMLThrowsException() throws Exception {
        String request = "{ \"sample\": \"data\" }";
        String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
        String requestJson = "{\"data_base64\":\"" + base64 + "\"}";

        when(nilRouterCommonUtility.convertToMap(requestJson)).thenReturn(mockPayload);
        when(camt59XmlProcessor.validateRequest(mockPayload)).thenReturn(false);
        doThrow(new RuntimeException("Processing failed")).when(camt59XmlProcessor).processXML(mockPayload);

        Mono<ResponseEntity<Response>> result = processController.process(requestJson);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(500, response.getStatusCodeValue());
                    assertEquals("ERROR", response.getBody().getStatus());
                    assertEquals("Message Processing Failed", response.getBody().getMessage());
                })
                .verifyComplete();
    }

    @Test
    void testProcess_Error_WhenConvertToMapThrowsException() throws Exception {
        String request = "{ \"sample\": \"data\" }";
        String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
        String requestJson = "{\"data_base64\":\"" + base64 + "\"}";

        when(nilRouterCommonUtility.convertToMap(requestJson)).thenThrow(new RuntimeException("Invalid JSON"));

        Mono<ResponseEntity<Response>> result = processController.process(requestJson);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(500, response.getStatusCodeValue());
                    assertEquals("ERROR", response.getBody().getStatus());
                    assertEquals("Message Processing Failed", response.getBody().getMessage());
                })
                .verifyComplete();

        verify(camt59XmlProcessor, never()).processXML(any());
    }

}
