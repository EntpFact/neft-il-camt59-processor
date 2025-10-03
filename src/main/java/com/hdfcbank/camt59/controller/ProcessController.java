package com.hdfcbank.camt59.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.camt59.exception.NILException;
import com.hdfcbank.camt59.model.ReqPayload;
import com.hdfcbank.camt59.model.Response;
import com.hdfcbank.camt59.service.Camt59XmlProcessor;
import com.hdfcbank.camt59.utils.NILRouterCommonUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@RestController
public class ProcessController {


    @Autowired
    Camt59XmlProcessor camt59XmlProcessor;

    @Autowired
    NILRouterCommonUtility nilRouterCommonUtility;
    @CrossOrigin
    @GetMapping(path = "/healthz")
    public ResponseEntity<?> healthz() {
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }

    @CrossOrigin
    @GetMapping(path = "/ready")
    public ResponseEntity<?> ready() {
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }




    @CrossOrigin
    @PostMapping("/process")
    public Mono<ResponseEntity<Response>> process(@RequestBody String request) throws JsonProcessingException {
        log.info("....CAMT59 Processing Started.... ");
        return Mono.fromCallable(() -> {
            try {
                ReqPayload requestMap = nilRouterCommonUtility.convertToMap(request);
                if(!camt59XmlProcessor.validateRequest(requestMap)){
                    camt59XmlProcessor.processXML(requestMap);
                }
                return ResponseEntity.ok(new Response("SUCCESS", "Message Processed."));
            } catch (Exception ex) {
                log.error("Failed in consuming the message: {}", ex);
                throw new NILException("Failed in consuming the message", ex);
            } finally {
                log.info("....CAMT59 Processing Completed.... ");
            }
        }).onErrorResume(ex -> {
            return Mono.just(new ResponseEntity<>(new Response("ERROR", "Message Processing Failed"), HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }

    @CrossOrigin
    @PostMapping("/sendToProcessor")
    public Mono<ResponseEntity<Response>> sendToProcessor(@RequestBody String request) throws JsonProcessingException {
        log.info("....CAMT59 Processing Started.... ");
        return Mono.fromCallable(() -> {
            try {
                ReqPayload requestMap = nilRouterCommonUtility.convertToMap(request);
                if(!camt59XmlProcessor.validateRequest(requestMap)){
                    camt59XmlProcessor.processXML(requestMap);
                }else {

                    camt59XmlProcessor.saveInvalidPayload(requestMap);
                    return ResponseEntity.ok(new Response("BAD REQUEST","Invalid Request"));
                }
                return ResponseEntity.ok(new Response("SUCCESS", "Message sent to Kafka:"));
            } catch (Exception ex) {
                log.error("Failed in consuming the message: {}", ex);
                throw new NILException("Failed in consuming the message", ex);
            } finally {
                log.info("....CAMT59 Processing Completed.... ");
            }
        }).onErrorResume(ex -> {
            return Mono.just(new ResponseEntity<>(new Response("ERROR", "Error processing to Kafka:"), HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }

}
