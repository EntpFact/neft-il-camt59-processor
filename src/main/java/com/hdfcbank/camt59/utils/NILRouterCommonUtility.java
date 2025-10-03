package com.hdfcbank.camt59.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.camt59.exception.NILException;
import com.hdfcbank.camt59.model.ReqPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class NILRouterCommonUtility {

    @Autowired
    ObjectMapper objectMapper;

    public String getBizMsgIdr(Document originalDoc) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", originalDoc, XPathConstants.NODE);
        String msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;
        return msgId;
    }

    public String getMsgDefIdr(Document originalDoc) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='MsgDefIdr']", originalDoc, XPathConstants.NODE);
        String msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;
        return msgId;

    }

    public ReqPayload convertToMap(String request) {
        try {
            if (request == null || request.trim().isEmpty()) {
                return null;
            }

            String base64 = Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8));
            String requestJson = "{\"data_base64\":\"" + base64 + "\"}";
            JsonNode rootNode = objectMapper.readTree(requestJson);
            String base64Data = rootNode.get("data_base64").asText();
            String reqPayloadString = new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
            return objectMapper.readValue(reqPayloadString,  ReqPayload.class);
        } catch (Exception e) {
            log.error("Failed to convert request string to map", e);
            throw new NILException("Invalid request format. Expecting JSON object.", e);
        }
    }

    public BigDecimal getTotalAmount(Document originalDoc) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String totalAmountString = (String) xpath.evaluate("//*[local-name()='GrpHdr']/*[local-name()='TtlIntrBkSttlmAmt']", originalDoc, XPathConstants.STRING);
        BigDecimal totalAmount = new BigDecimal(totalAmountString);
        return totalAmount;

    }

    public String evaluateText(XPath xpath, Node node, String expression) {
        try {
            Node result = (Node) xpath.evaluate(expression, node, XPathConstants.NODE);
            return result != null ? result.getTextContent().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isOutward(String xml) {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));

            String fromMmbId = doc.getElementsByTagName("Fr").item(0)
                    .getTextContent().trim().toUpperCase();

            String toMmbId = doc.getElementsByTagName("To").item(0)
                    .getTextContent().trim().toUpperCase();
            if (fromMmbId.contains("HDFC") && toMmbId.contains("RBI")) {
                // Outward flow
                return true;
            }

        } catch (ParserConfigurationException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        } catch (SAXException e) {
            log.error(e.toString());
        }

        return false;
    }

    /**
     * Parses an XML string into a Document object with namespace awareness.
     */
    public static Document parseXmlStringToDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Converts a Document object to its XML string representation.
     */
    public static String documentToXmlString(Document doc) throws Exception {
        StringWriter writer = new StringWriter();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

}
