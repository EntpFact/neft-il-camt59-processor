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

    private ReqPayload mockReqPayload;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockReqPayload = new ReqPayload(); // create mock request payload
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
        String request = "{\"data\":\"test\"}";

        when(nilRouterCommonUtility.convertToMap(request)).thenReturn(mockReqPayload);
        when(camt59XmlProcessor.validateRequest(any(ReqPayload.class))).thenReturn(false);
        doNothing().when(camt59XmlProcessor).processXML(any(ReqPayload.class));

        Mono<ResponseEntity<Response>> result = processController.process(request);


//        verify(camt59XmlProcessor, times(1)).processXML(any(ReqPayload.class));
    }

}
