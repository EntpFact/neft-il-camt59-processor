package com.hdfcbank.camt59.controller;

import com.hdfcbank.camt59.model.ReqPayload;
import com.hdfcbank.camt59.model.Response;
import com.hdfcbank.camt59.service.Camt59XmlProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;

@WebFluxTest(ProcessController.class)
class ProcessControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Mock
    private Camt59XmlProcessor camt59XmlProcessor;

    // Health check
//    @Test
    void testHealthzEndpoint() {
        webTestClient.get()
                .uri("/healthz")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Success");
    }

    // Readiness check
//    @Test
    void testReadyEndpoint() {
        webTestClient.get()
                .uri("/ready")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Success");
    }
//
    // Process endpoint - success
//    @Test
    void testProcessEndpointSuccess() {
        ReqPayload payload = new ReqPayload(); // Populate with required fields

        Mockito.doNothing().when(camt59XmlProcessor).processXML(Mockito.any());

        webTestClient.post()
                .uri("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Response.class)
                .value(response -> {
                    assert response.getStatus().equals("SUCCESS");
                    assert response.getMessage().equals("Message Processed.");
                });
    }
//    @Test
    void testProcessEndpoint() {
        String request = "<test>valid xml</test>";

        webTestClient.post().uri("/process").
                bodyValue(request).exchange().expectStatus()
                .is4xxClientError().expectBody().jsonPath("$.status").isEqualTo("404");
    }
}
