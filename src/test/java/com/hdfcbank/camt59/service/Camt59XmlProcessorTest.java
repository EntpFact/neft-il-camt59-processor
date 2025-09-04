package com.hdfcbank.camt59.service;

import com.hdfcbank.camt59.dao.NilRepository;
import com.hdfcbank.camt59.kafkaproducer.KafkaUtils;
import com.hdfcbank.camt59.model.*;
import com.hdfcbank.camt59.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Camt59XmlProcessorTest {

    @InjectMocks
    private Camt59XmlProcessor camt59XmlProcessor;

    @Mock
    private NilRepository dao;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private KafkaUtils kafkaUtils;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private String sampleXml =
            "<RequestPayload>" +
                    "<AppHdr>" +
                    "<BizMsgIdr>MSG123</BizMsgIdr>" +
                    "<CreDt>2025-08-28T10:15:30Z</CreDt>" +
                    "</AppHdr>" +
                    "<Document>" +
                    "<NtfctnToRcvStsRpt>" +
                    "<GrpHdr><MsgId>GH001</MsgId></GrpHdr>" +
                    "<OrgnlNtfctnAndSts>" +
                    "<OrgnlNtfctnRef>" +
                    "<DbtrAgt><FinInstnId><BICFI>HDFCINBB</BICFI></FinInstnId></DbtrAgt>" +
                    "<OrgnlItmAndSts>" +
                    "<OrgnlItmId>ABCDEFGHIJKLMN555</OrgnlItmId>" + // 15th char = 0 (FC)
                    "<OrgnlEndToEndId>E2E001</OrgnlEndToEndId>" +
                    "<Amt>100.00</Amt>" +
                    "</OrgnlItmAndSts>" +
                    "</OrgnlNtfctnRef>" +
                    "</OrgnlNtfctnAndSts>" +
                    "</NtfctnToRcvStsRpt>" +
                    "</Document>" +
                    "</RequestPayload>";

    @Test
    void testProcessXML_withInwardFlowType_FC() throws XPathExpressionException {
        ReqPayload payload = new ReqPayload();
        Header header = new Header();
        header.setFlowType("INWARD");
        header.setPrefix("TEST_");
        payload.setHeader(header);

        Body body = new Body();
        body.setPayload(sampleXml);
        payload.setBody(body);

        when(utilityMethods.getBizMsgIdr(any(Document.class))).thenReturn("MSG123");
        when(utilityMethods.getMsgDefIdr(any(Document.class))).thenReturn("camt.059.001.06");

        camt59XmlProcessor.processXML(payload);

        // Verify DB save
//        verify(dao, atLeastOnce()).saveDataInMsgEventTracker(any(MsgEventTracker.class));

        // Verify Kafka publish
//        verify(kafkaUtils, atLeastOnce()).publishToResponseTopic(anyString(), anyString());
    }





    @Test
    void testExtractTransactions() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(sampleXml.getBytes()));

        Camt59Fields field = new Camt59Fields("MSG123", "E2E001", "ABCDEFGHIJKLMNO0", "100.00", "DISPATCHED_FC");

        when(utilityMethods.getBizMsgIdr(doc)).thenReturn("MSG123");
        when(utilityMethods.getMsgDefIdr(doc)).thenReturn("camt.059.001.06");

        List<TransactionAudit> audits = camt59XmlProcessor.extractCamt59Transactions(
                doc, sampleXml, List.of(field), LocalDate.now(), LocalDateTime.now()
        );

        assertEquals(1, audits.size());
        assertEquals("MSG123", audits.get(0).getMsgId());
        assertEquals("E2E001", audits.get(0).getEndToEndId());
        assertEquals("DISPATCHED_FC", audits.get(0).getTarget());
    }

    @Test
    void testExtractOrgnlItmIdDigit_valid() {
        int digit = invokePrivateExtractOrgnlItmIdDigit("ABCDEFGHIJKLMN55");
        assertEquals(5, digit);
    }

    @Test
    void testExtractOrgnlItmIdDigit_invalid() {
        int digit = invokePrivateExtractOrgnlItmIdDigit("SHORTID");
        assertEquals(-1, digit);
    }

    // helper to call private static method
    private int invokePrivateExtractOrgnlItmIdDigit(String input) {
        try {
            var method = Camt59XmlProcessor.class.getDeclaredMethod("extractOrgnlItmIdDigit", String.class);
            method.setAccessible(true);
            return (int) method.invoke(null, input);
        } catch (Exception e) {
            fail("Reflection call failed: " + e.getMessage());
            return -1;
        }
    }


    @Test
    public void testParseXml_InwardFlow() throws Exception {
        String xml = "<RequestPayload><AppHdr><BizMsgIdr>ID456</BizMsgIdr><MsgDefIdr>pacs.004.001.10</MsgDefIdr>  <CreDt>2006-08-22T12:12:00Z</CreDt>" +
                "</AppHdr><Document><TxInf><OrgnlTxId>HDFCN52022062824954014</OrgnlTxId>" +
                "<OrgnlEndToEndId>123456789012345</OrgnlEndToEndId><RtrdIntrBkSttlmAmt>1000</RtrdIntrBkSttlmAmt></TxInf>" +
                "</Document></RequestPayload>";
        ReqPayload payload=new ReqPayload();
        Body body=new Body();
        Header header=new Header();
        body.setPayload(sampleXml);
        header.setFlowType("INWARD");
        header.setInvalidPayload(false);
        header.setPrefix("CBS");
        header.setTarget("FC");

        payload.setBody(body);
        payload.setHeader(header);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("ID456");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        camt59XmlProcessor.processXML(payload);

//        verify(dao, atLeastOnce()).saveDataInMsgEventTracker(any());
    }


    @Test
    public void testParseXml_InwardFlow2() throws Exception {

        String xml =
                "<RequestPayload>" +
                        "<AppHdr>" +
                        "<BizMsgIdr>MSG123</BizMsgIdr>" +
                        "<CreDt>2025-08-28T10:15:30Z</CreDt>" +
                        "</AppHdr>" +
                        "<Document>" +
                        "<NtfctnToRcvStsRpt>" +
                        "<GrpHdr><MsgId>GH001</MsgId></GrpHdr>" +
                        "<OrgnlNtfctnAndSts>" +
                        "<OrgnlNtfctnRef>" +
                        "<DbtrAgt><FinInstnId><BICFI>HDFCINBB</BICFI></FinInstnId></DbtrAgt>" +
                        "<OrgnlItmAndSts>" +
                        "<OrgnlItmId>ABCDEFGHIJKLMN55</OrgnlItmId>" + // 15th char = 5 (EPH)
                        "<OrgnlEndToEndId>E2E001</OrgnlEndToEndId>" +
                        "<Amt>100.00</Amt>" +
                        "</OrgnlItmAndSts>" +
                        "<OrgnlItmAndSts>" +
                        "<OrgnlItmId>ABCDEFGHIJKLMN15</OrgnlItmId>" + // 15th char = 1 (FC)
                        "<OrgnlEndToEndId>E2E001</OrgnlEndToEndId>" +
                        "<Amt>100.00</Amt>" +
                        "</OrgnlItmAndSts>" +
                        "</OrgnlNtfctnRef>" +
                        "</OrgnlNtfctnAndSts>" +
                        "</NtfctnToRcvStsRpt>" +
                        "</Document>" +
                        "</RequestPayload>";

        ReqPayload payload=new ReqPayload();
        Body body=new Body();
        Header header=new Header();
        body.setPayload(xml);
        header.setFlowType("INWARD");
        header.setInvalidPayload(false);
        header.setPrefix("CBS");
        header.setTarget("FC");

        payload.setBody(body);
        payload.setHeader(header);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("ID456");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        camt59XmlProcessor.processXML(payload);

//        verify(dao, atLeastOnce()).saveDataInMsgEventTracker(any());
    }

    @Test
    public void testParseXml_InwardFlow3() throws Exception {

        String xml =
                "<RequestPayload>" +
                        "<AppHdr>" +
                        "<BizMsgIdr>MSG123</BizMsgIdr>" +
                        "<CreDt>2025-08-28T10:15:30Z</CreDt>" +
                        "</AppHdr>" +
                        "<Document>" +
                        "<NtfctnToRcvStsRpt>" +
                        "<GrpHdr><MsgId>GH001</MsgId></GrpHdr>" +
                        "<OrgnlNtfctnAndSts>" +
                        "<OrgnlNtfctnRef>" +
                        "<DbtrAgt><FinInstnId><BICFI>HDFCINBB</BICFI></FinInstnId></DbtrAgt>" +
                        "<OrgnlItmAndSts>" +
                        "<OrgnlItmId>ABCDEFGHIJKLMN05</OrgnlItmId>" +
                        "<OrgnlEndToEndId>E2E001</OrgnlEndToEndId>" +
                        "<Amt>100.00</Amt>" +
                        "</OrgnlItmAndSts>" +
                        "<OrgnlItmAndSts>" +
                        "<OrgnlItmId>ABCDEFGHIJKLMN15</OrgnlItmId>" + // 15th char = 1 (FC)
                        "<OrgnlEndToEndId>E2E001</OrgnlEndToEndId>" +
                        "<Amt>100.00</Amt>" +
                        "</OrgnlItmAndSts>" +
                        "</OrgnlNtfctnRef>" +
                        "</OrgnlNtfctnAndSts>" +
                        "</NtfctnToRcvStsRpt>" +
                        "</Document>" +
                        "</RequestPayload>";
        ReqPayload payload=new ReqPayload();
        Body body=new Body();
        Header header=new Header();
        body.setPayload(xml);
        header.setFlowType("INWARD");
        header.setInvalidPayload(false);
        header.setPrefix("CBS");
        header.setTarget("FC");

        payload.setBody(body);
        payload.setHeader(header);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("ID456");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        camt59XmlProcessor.processXML(payload);

//        verify(dao, atLeastOnce()).saveDataInMsgEventTracker(any());
    }


}
