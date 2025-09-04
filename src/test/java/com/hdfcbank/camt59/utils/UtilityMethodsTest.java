package com.hdfcbank.camt59.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UtilityMethodsTest {

    private UtilityMethods utility;
    private Document sampleDoc;

    private final String sampleXml = """
        <Envelope xmlns="urn:iso:std:iso:20022:tech:xsd:camt.059.001.01">
            <AppHdr>
                <BizMsgIdr>ABC123456789</BizMsgIdr>
                <MsgDefIdr>camt.059.001.01</MsgDefIdr>
            </AppHdr>
            <Document>
                <GrpHdr>
                    <TtlIntrBkSttlmAmt>98765.43</TtlIntrBkSttlmAmt>
                </GrpHdr>
            </Document>
        </Envelope>
        """;

    private final String outwardXml = """
        <Envelope>
            <Fr>HDFCINBB</Fr>
            <To>RBISINBB</To>
        </Envelope>
        """;

    private final String inwardXml = """
        <Envelope>
            <Fr>RBISINBB</Fr>
            <To>HDFCINBB</To>
        </Envelope>
        """;

    @BeforeEach
    void setUp() throws Exception {
        utility = new UtilityMethods();
        sampleDoc = parseXml(sampleXml);
    }

    private Document parseXml(String xml) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    @Test
    void testGetBizMsgIdr() throws Exception {
        String result = utility.getBizMsgIdr(sampleDoc);
        assertEquals("ABC123456789", result);
    }

    @Test
    void testGetMsgDefIdr() throws Exception {
        String result = utility.getMsgDefIdr(sampleDoc);
        assertEquals("camt.059.001.01", result);
    }

    @Test
    void testGetTotalAmount() throws Exception {
        BigDecimal amount = utility.getTotalAmount(sampleDoc);
        assertEquals(new BigDecimal("98765.43"), amount);
    }

    @Test
    void testEvaluateText() throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node node = sampleDoc.getDocumentElement();
        String result = utility.evaluateText(xpath, node, "//*[local-name()='BizMsgIdr']");
        assertEquals("ABC123456789", result);
    }

    @Test
    void testIsOutwardTrue() {
        boolean result = utility.isOutward(outwardXml);
        assertTrue(result);
    }

    @Test
    void testIsOutwardFalse() {
        boolean result = utility.isOutward(inwardXml);
        assertFalse(result);
    }

    @Test
    void testIsOutwardMalformedXml() {
        String malformedXml = "<Envelope><Fr>HDFC</Fr><To>"; // incomplete
        boolean result = utility.isOutward(malformedXml);
        assertFalse(result); // should handle exception and return false
    }
}
