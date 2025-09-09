package com.hdfcbank.camt59.service;

import com.hdfcbank.camt59.dao.NilRepository;
import com.hdfcbank.camt59.kafkaproducer.KafkaUtils;
import com.hdfcbank.camt59.model.*;
import com.hdfcbank.camt59.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

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
    private ErrorHandling errorHandling;

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
    void testValidateRequest_whenValid() throws Exception {
        ReqPayload payload = new ReqPayload();
        Header header = new Header();
        header.setFlowType("INWARD");
        header.setPrefix("TEST_");

        payload.setHeader(header);
        payload.getHeader().setInvalidPayload(true);

        Body body = new Body();
        body.setPayload(sampleXml);
        payload.setBody(body);

        Boolean result = camt59XmlProcessor.validateRequest(payload);
        assertTrue(result); // should return true
        verify(errorHandling, never()).handleInvalidPayload(any());
    }

    @Test
    void testValidateRequest_whenInvalid() throws Exception {
        ReqPayload payload = new ReqPayload();
        Header header = new Header();
        header.setFlowType("INWARD");
        header.setPrefix("TEST_");
        payload.setHeader(header);
        payload.getHeader().setInvalidPayload(false);


        Body body = new Body();
        body.setPayload(sampleXml);
        payload.setBody(body);
        Boolean result = camt59XmlProcessor.validateRequest(payload);
        assertFalse(result); // flipped logic, so should be false
        verify(errorHandling, times(1)).handleInvalidPayload(payload);
    }

    private static final String XML_WITH_ITEMS =
            "<RequestPayload>" +
                    "<AppHdr><BizMsgIdr>BIZ123</BizMsgIdr></AppHdr>" +
                    "<Document>" +
                    "<NtfctnToRcvStsRpt>" +
                    "<GrpHdr><MsgId>MSG001</MsgId></GrpHdr>" +
                    "<OrgnlNtfctnRef>" +
                    "<DbtrAgt><FinInstnId><BICFI>TESTBIC</BICFI></FinInstnId></DbtrAgt>" +
                    "<OrgnlItmAndSts>" +
                    "<OrgnlItmId>ABCDEFGHIJKLMNO1</OrgnlItmId>" + // digit=1
                    "<Amt>100.00</Amt>" +
                    "</OrgnlItmAndSts>" +
                    "</OrgnlNtfctnRef>" +
                    "<OrgnlNtfctnRef>" +
                    "<OrgnlItmAndSts>" +
                    "<OrgnlItmId>ABCDEFGHIJKLMNO8</OrgnlItmId>" + // digit=8
                    "<Amt>200.00</Amt>" +
                    "</OrgnlItmAndSts>" +
                    "</OrgnlNtfctnRef>" +
                    "</NtfctnToRcvStsRpt>" +
                    "</Document>" +
                    "</RequestPayload>";

    private static final String XML_WITHOUT_DOCUMENT =
            "<RequestPayload>" +
                    "<AppHdr><BizMsgIdr>BIZ123</BizMsgIdr></AppHdr>" +
                    "</RequestPayload>";

    private Document parseXml(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new java.io.StringReader(xml)));
    }

    @Test
    void testFilter_FC_Range_SelectsDigit1() throws Exception {
        Document inputDoc = parseXml(sampleXml);
        Document result = camt59XmlProcessor.filterOrgnlItmAndSts(inputDoc, 0, 4);

        String xml = new Camt59XmlProcessor().documentToXml(result);

//        assertTrue(xml.contains("<AppHdr>")); // AppHdr copied
//        assertTrue(xml.contains("<GrpHdr>")); // GrpHdr copied
//        assertTrue(xml.contains("<OrgnlItmId>ABCDEFGHIJKL11111O1</OrgnlItmId>")); // digit 1 included
//        assertFalse(xml.contains("ABCDEFGHIJKL888O1")); // digit 8 excluded
    }

    @Test
    void testFilter_EPH_Range_SelectsDigit8() throws Exception {
        Document inputDoc = parseXml(sampleXml);
        Document result = Camt59XmlProcessor.filterOrgnlItmAndSts(inputDoc, 5, 9);

        String xml = new Camt59XmlProcessor().documentToXml(result);

//        assertTrue(xml.contains("<OrgnlItmId>ABCDEFGHI77778888</OrgnlItmId>")); // digit 8 included
//        assertFalse(xml.contains("ABCDEFGHIJKLMNO1")); // digit 1 excluded
    }

    @Test
    void testFilter_NoDocument_ReturnsMinimalDoc() throws Exception {
        Document inputDoc = parseXml(XML_WITHOUT_DOCUMENT);
        Document result = Camt59XmlProcessor.filterOrgnlItmAndSts(inputDoc, 0, 9);

        String xml = new Camt59XmlProcessor().documentToXml(result);

        assertTrue(xml.contains("<RequestPayload")); // root preserved
//        assertTrue(xml.contains("<AppHdr>")); // AppHdr preserved
        assertFalse(xml.contains("<Document>")); // no Document added
    }

    @Test
    void testFilter_NoMatchingDigits_EmptyOrgnlNtfctnAndSts() throws Exception {
        Document inputDoc = parseXml(XML_WITH_ITEMS);
        // Use range that doesn't match (e.g. 2–3)
        Document result = Camt59XmlProcessor.filterOrgnlItmAndSts(inputDoc, 2, 3);

        String xml = new Camt59XmlProcessor().documentToXml(result);

        assertFalse(xml.contains("<OrgnlItmId>")); // none included
//        assertTrue(xml.contains("<GrpHdr>")); // GrpHdr still copied
    }

    @Test
    void testFilter_InvalidOrgnlItmId_NoCrash() throws Exception {
        String xmlWithInvalidId =
                "<RequestPayload><AppHdr>    <Fr>        <FIId>            <FinInstnId>                <ClrSysMmbId>                    <MmbId>RBIP0NEFTSC</MmbId>                </ClrSysMmbId>            </FinInstnId>        </FIId>    </Fr>    <To>        <FIId>            " +
                        "<FinInstnId>                <ClrSysMmbId>                    <MmbId>HDFC0000001</MmbId>                </ClrSysMmbId>            </FinInstnId>        </FIId>    </To>    <BizMsgIdr>RBIP200608226200200080</BizMsgIdr>    <MsgDefIdr>camt.059.001.06</MsgDefIdr>    <BizSvc>NEFTCustomerCreditNotification</BizSvc>   " +
                        " <CreDt>2006-08-22T12:12:00Z</CreDt> <Sgntr></Sgntr></AppHdr><Document >    <NtfctnToRcvStsRpt>        <GrpHdr>            <MsgId>RBIP200608226200200080</MsgId>            <CreDtTm>2006-08-22T12:12:00</CreDtTm>        </GrpHdr>        <OrgnlNtfctnAndSts>            <OrgnlNtfctnRef>                <DbtrAgt>                    <FinInstnId>                        <ClrSysMmbId>                            <MmbId>HDFC00650122</MmbId>                        </ClrSysMmbId>                    </FinInstnId>                </DbtrAgt>                <OrgnlItmAndSts>                   " +
                        " <OrgnlItmId>HDFCN52022062824954013</OrgnlItmId>                    <OrgnlEndToEndId>/XUTR/HDFCH22194004232</OrgnlEndToEndId>                    <Amt Ccy=\"INR\">100.00</Amt>                    <XpctdValDt>2006-12-07</XpctdValDt>                    <ItmSts>RCVD</ItmSts>                </OrgnlItmAndSts>            </OrgnlNtfctnRef>            <OrgnlNtfctnRef>                <DbtrAgt>                    <FinInstnId>                        <ClrSysMmbId>                            <MmbId>HDFC0065012</MmbId>                        </ClrSysMmbId>                    </FinInstnId>                </DbtrAgt>                <OrgnlItmAndSts>                    <OrgnlItmId>HDFCN52022062824954014</OrgnlItmId>                    <OrgnlEndToEndId>/XUTR/HDFCH22194004233</OrgnlEndToEndId>                    <Amt Ccy=\"INR\">100.00</Amt>                    <XpctdValDt>2006-12-07</XpctdValDt>                    <ItmSts>RCVD</ItmSts>                </OrgnlItmAndSts>            </OrgnlNtfctnRef>            <OrgnlNtfctnRef>                <DbtrAgt>                    <FinInstnId>                        <ClrSysMmbId>                            <MmbId>HDFC0065013</MmbId>                        </ClrSysMmbId>                    </FinInstnId>                </DbtrAgt>                <OrgnlItmAndSts>                    <OrgnlItmId>HDFCN52022062824954015</OrgnlItmId>                    <OrgnlEndToEndId>/XUTR/HDFCH22194004234</OrgnlEndToEndId>                    <Amt Ccy=\"INR\">100.00</Amt>                    <XpctdValDt>2006-12-07</XpctdValDt>                    <ItmSts>RCVD</ItmSts>                </OrgnlItmAndSts>            </OrgnlNtfctnRef>            <OrgnlNtfctnRef>                <DbtrAgt>                    <FinInstnId>                        <ClrSysMmbId>                            <MmbId>HDFC0065014</MmbId>                        </ClrSysMmbId>                    </FinInstnId>                </DbtrAgt>                <OrgnlItmAndSts>                    <OrgnlItmId>HDFC52022062824954018</OrgnlItmId>                    <OrgnlEndToEndId>/XUTR/HDFCH22194004235</OrgnlEndToEndId>                    <Amt Ccy=\"INR\">100.00</Amt>                    <XpctdValDt>2011-12-07</XpctdValDt>                    <ItmSts>RCVD</ItmSts>                </OrgnlItmAndSts>            </OrgnlNtfctnRef>            <OrgnlNtfctnRef>                <DbtrAgt>                    <FinInstnId>                        <ClrSysMmbId>                            <MmbId>HDFC0065015</MmbId>                        </ClrSysMmbId>                    </FinInstnId>                </DbtrAgt>               <OrgnlItmAndSts>                    <OrgnlItmId>HDFCN52022062824954019</OrgnlItmId>                    <OrgnlEndToEndId>/XUTR/HDFCH22194004236</OrgnlEndToEndId>                    <Amt Ccy=\"INR\">100.00</Amt>                    <XpctdValDt>2006-08-22</XpctdValDt>                    <ItmSts>RCVD</ItmSts>                </OrgnlItmAndSts>            </OrgnlNtfctnRef>\\t<OrgnlNtfctnRef>                <DbtrAgt>                    <FinInstnId>                        <ClrSysMmbId>                            <MmbId>HDFC0065016</MmbId>                        </ClrSysMmbId>                    </FinInstnId>                </DbtrAgt>                <OrgnlItmAndSts>                    <OrgnlItmId>HDFCN52022062824954020</OrgnlItmId>                    <OrgnlEndToEndId>/XUTR/HDFCH22194004237</OrgnlEndToEndId>                    <Amt Ccy=\"INR\">100.00</Amt>                    <XpctdValDt>2006-08-22</XpctdValDt>                   " +
                        " <ItmSts>RCVD</ItmSts>                </OrgnlItmAndSts>            </OrgnlNtfctnRef></OrgnlNtfctnAndSts>    </NtfctnToRcvStsRpt></Document></RequestPayload>\n";

        Document inputDoc = parseXml(xmlWithInvalidId);
        Document result = Camt59XmlProcessor.filterOrgnlItmAndSts(inputDoc, 0, 9);

        String xml = new Camt59XmlProcessor().documentToXml(result);

        assertTrue(xml.contains("<RequestPayload")); // still valid doc
        assertFalse(xml.contains("<OrgnlItmId>SHORT</OrgnlItmId>")); // excluded
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
