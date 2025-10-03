package com.hdfcbank.camt59.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hdfcbank.camt59.dao.NilRepository;
import com.hdfcbank.camt59.kafkaproducer.KafkaUtils;
import com.hdfcbank.camt59.model.*;
import com.hdfcbank.camt59.utils.Constants;
import com.hdfcbank.camt59.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class Camt59XmlProcessor {

    @Value("${topic.sfmstopic}")
    private String sfmsTopic;

    @Value("${topic.fctopic}")
    private String fcTopic;

    @Value("${topic.ephtopic}")
    private String ephTopic;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Autowired
    private NilRepository dao;

    @Autowired
    private UtilityMethods utilityMethods;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Autowired
    ErrorHandling errorHandling;

    public void processXML(ReqPayload payload) {
        Optional.ofNullable(payload.getHeader())
                .filter(header -> "INWARD".equalsIgnoreCase(header.getFlowType()))
                .ifPresent(header -> processCamt59InwardMessage(payload.getBody().getPayload(), payload));
    }

    private void processCamt59InwardMessage(String xml, ReqPayload payload) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            XPath xpath = XPathFactory.newInstance().newXPath();

            String bizMsgIdr = xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", document);
            String batchCreationTime = xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']/*[local-name()='CreDt']", document);

            LocalDateTime batchCreationTimeStamp = LocalDateTime.parse(batchCreationTime, DateTimeFormatter.ISO_DATE_TIME);
            LocalDate batchCreationDate = Instant.parse(batchCreationTime).atZone(ZoneId.of("UTC")).toLocalDate();

            NodeList orgnlItmAndStsList = (NodeList) xpath.evaluate(
                    "/*[local-name()='RequestPayload']/*[local-name()='Document']//*[local-name()='OrgnlItmAndSts']",
                    document, XPathConstants.NODESET);

            List<Camt59Fields> camt59Fields = IntStream.range(0, orgnlItmAndStsList.getLength())
                    .mapToObj(orgnlItmAndStsList::item)
                    .filter(Element.class::isInstance)
                    .map(Element.class::cast)
                    .map(elem -> buildCamt59Field(elem, xpath, bizMsgIdr))
                    .collect(Collectors.toList());

            boolean invalidReq = payload.getHeader().isInvalidPayload();
            String prefix = payload.getHeader().getPrefix();
            String flowType = payload.getHeader().getFlowType();

            double consolidateAmountFC = sumAmounts(camt59Fields, "DISPATCHED_FC");
            double consolidateAmountEPH = sumAmounts(camt59Fields, "DISPATCHED_EPH");

            List<String> targets = camt59Fields.stream()
                    .map(Camt59Fields::getSwtch)
                    .distinct()
                    .collect(Collectors.toList());

            if (targets.contains("DISPATCHED_FC")) {
                handleTarget(payload,document, xml, camt59Fields, "FC", 0, 4, consolidateAmountFC,
                        batchCreationDate, batchCreationTimeStamp, invalidReq, prefix, flowType, fcTopic);
            }
            if (targets.contains("DISPATCHED_EPH")) {
                handleTarget(payload,document, xml, camt59Fields, "EPH", 5, 9, consolidateAmountEPH,
                        batchCreationDate, batchCreationTimeStamp, invalidReq, prefix, flowType, ephTopic);
            }

            List<TransactionAudit> transactionAudits =
                    extractCamt59Transactions(document, xml, camt59Fields, batchCreationDate, batchCreationTimeStamp);
            dao.saveAllTransactionAudits(transactionAudits);

        } catch (Exception e) {
            log.error("Error processing CAMT.59: {}", e.getMessage(), e);
        }
    }

    private Camt59Fields buildCamt59Field(Element item, XPath xpath, String bizMsgIdr) {
        try {
            String amount = xpath.evaluate("./*[local-name()='Amt']", item);
            String orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlItmId']", item);
            String orgnlEndToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", item);

            int digit = extractOrgnlItmIdDigit(orgnlItmId);
            String swtch = (digit >= 0 && digit <= 4) ? "DISPATCHED_FC" : "DISPATCHED_EPH";

            return new Camt59Fields(bizMsgIdr, orgnlEndToEndId, orgnlItmId, amount, swtch);
        } catch (Exception e) {
            log.error("Error building Camt59Fields: {}", e.getMessage());
            return null;
        }
    }

    private void handleTarget(ReqPayload payload,Document document, String xml, List<Camt59Fields> camt59Fields,
                              String target, int minDigit, int maxDigit, double consolidateAmount,
                              LocalDate batchDate, LocalDateTime batchTime, boolean invalidReq,
                              String prefix, String flowType, String topic) throws Exception {

        Document filteredDoc = filterOrgnlItmAndSts(document, minDigit, maxDigit);
        String outputXml = documentToXml(filteredDoc);

        MsgEventTracker tracker = new MsgEventTracker();
        tracker.setMsgId(utilityMethods.getBizMsgIdr(document));
        tracker.setSource("SFMS");
        tracker.setTarget("DISPATCHER_" + target);
        tracker.setFlowType(flowType);
        tracker.setBatchId(" ");
        tracker.setStatus(Constants.SENT_TO_DISPATCHER);
        tracker.setMsgType(utilityMethods.getMsgDefIdr(document));
        tracker.setOrgnlReq(prefix + xml);
        tracker.setBatchCreationTime(batchTime);
        tracker.setBatchCreationDate(batchDate);
        tracker.setInvalidPayload(invalidReq);
        tracker.setConsolidateAmt(BigDecimal.valueOf(consolidateAmount));
        tracker.setTransformedJsonReq(payload);
        tracker.setIntermediateReq(prefix + outputXml);

        long count = camt59Fields.stream()
                .filter(f -> f.getSwtch().equals("DISPATCHED_" + target))
                .count();

        tracker.setIntermediateCount((int) count);
        tracker.setOrgnlReqCount(camt59Fields.size());

        dao.saveDataInMsgEventTracker(tracker);
        kafkaUtils.publishToResponseTopic(outputXml, topic,tracker.getMsgId());
    }


    public void saveInvalidPayload(ReqPayload requestMap) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException, SQLException {

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(requestMap.getBody().getPayload())));

        XPath xpath = XPathFactory.newInstance().newXPath();

        String bizMsgIdr =utilityMethods.getBizMsgIdr(document);
        MsgEventTracker tracker = new MsgEventTracker();
        tracker.setMsgId(bizMsgIdr);
        tracker.setSource("SFMS");
        tracker.setBatchId(" ");
        tracker.setTarget(requestMap.getHeader().getTarget());
        tracker.setOrgnlReq(requestMap.getHeader().getPrefix() + requestMap.getBody().getPayload());
        tracker.setInvalidPayload(requestMap.getHeader().isInvalidPayload());

        dao.saveDataInMsgEventTracker(tracker);
//        errorHandling.handleInvalidPayload(requestMap);

    }

    private double sumAmounts(List<Camt59Fields> fields, String swtch) {
        return fields.stream()
                .filter(f -> swtch.equals(f.getSwtch()))
                .mapToDouble(f -> Double.parseDouble(f.getAmount()))
                .sum();
    }

    public List<TransactionAudit> extractCamt59Transactions(Document doc, String xml,
                                                            List<Camt59Fields> fields,
                                                            LocalDate batchDate, LocalDateTime batchTime) {
        return fields.stream()
                .map(f -> {
                    TransactionAudit t = new TransactionAudit();
                    try {
                        t.setMsgId(utilityMethods.getBizMsgIdr(doc));
                    } catch (XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }
                    t.setEndToEndId(f.getEndToEndId());
                    t.setTxnId(f.getTxId());
                    t.setMsgType("camt.059.001.06");
                    t.setSource("SFMS");
                    t.setAmount(BigDecimal.valueOf(Double.parseDouble(f.getAmount())));
                    t.setTarget(f.getSwtch());
                    t.setBatchDate(batchDate);
                    t.setBatchTime(batchTime);
                    t.setFlowType("INWARD");
                    t.setReqPayload(xml);
                    return t;
                })
                .collect(Collectors.toList());
    }

    public String documentToXml(Document doc) throws TransformerException {
        StringWriter writer = new StringWriter();
        javax.xml.transform.TransformerFactory.newInstance()
                .newTransformer()
                .transform(new javax.xml.transform.dom.DOMSource(doc),
                        new javax.xml.transform.stream.StreamResult(writer));
        return writer.toString();
    }

    private static int extractOrgnlItmIdDigit(String orgnlItmId) {
        if (orgnlItmId != null && orgnlItmId.length() >= 15) {
            return Character.getNumericValue(orgnlItmId.charAt(14));
        }
        return -1;
    }
    public static Document filterOrgnlItmAndSts(Document document, int minDigit, int maxDigit) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document newDoc = factory.newDocumentBuilder().newDocument();

        // Copy <RequestPayload> root
        Element root = (Element) newDoc.importNode(document.getDocumentElement(), false);
        newDoc.appendChild(root);

        // Copy <AppHdr> if present
        NodeList children = document.getDocumentElement().getChildNodes();
        IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(node -> "AppHdr".equals(node.getLocalName()))
                .findFirst()
                .ifPresent(appHdr -> root.appendChild(newDoc.importNode(appHdr, true)));

        XPath xpath = XPathFactory.newInstance().newXPath();

        // Get <Document> node
        NodeList docNodes = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']",
                document, XPathConstants.NODESET);

        if (docNodes.getLength() == 0) {
            return newDoc; // no <Document> -> return minimal doc
        }

        Element originalDocument = (Element) docNodes.item(0);
        String nsUri = originalDocument.getNamespaceURI();

        Element newDocumentElem = newDoc.createElementNS(nsUri, "Document");
        root.appendChild(newDocumentElem);

        Element ntfctnToRcvStsRpt = newDoc.createElementNS(nsUri, "NtfctnToRcvStsRpt");
        newDocumentElem.appendChild(ntfctnToRcvStsRpt);

        // Copy <GrpHdr> if exists
        NodeList grpHdrList = originalDocument.getElementsByTagNameNS("*", "GrpHdr");
        if (grpHdrList.getLength() > 0) {
            ntfctnToRcvStsRpt.appendChild(newDoc.importNode(grpHdrList.item(0), true));
        }

        // Process <OrgnlNtfctnRef>
        NodeList orgnlNtfctnRefs = originalDocument.getElementsByTagNameNS("*", "OrgnlNtfctnRef");
        Element newOrgnlNtfctnAndSts = newDoc.createElementNS(nsUri, "OrgnlNtfctnAndSts");

        IntStream.range(0, orgnlNtfctnRefs.getLength())
                .mapToObj(orgnlNtfctnRefs::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .forEach(ref -> {
                    NodeList itmAndStsList = ref.getElementsByTagNameNS("*", "OrgnlItmAndSts");
                    IntStream.range(0, itmAndStsList.getLength())
                            .mapToObj(itmAndStsList::item)
                            .filter(Element.class::isInstance)
                            .map(Element.class::cast)
                            .forEach(itmAndSts -> {
                                try {
                                    String orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlItmId']", itmAndSts);
                                    int digit = extractOrgnlItmIdDigit(orgnlItmId);

                                    if (digit >= minDigit && digit <= maxDigit) {
                                        Element newRef = newDoc.createElementNS(nsUri, "OrgnlNtfctnRef");

                                        NodeList dbtrAgtList = ref.getElementsByTagNameNS("*", "DbtrAgt");
                                        if (dbtrAgtList.getLength() > 0) {
                                            newRef.appendChild(newDoc.importNode(dbtrAgtList.item(0), true));
                                        }

                                        newRef.appendChild(newDoc.importNode(itmAndSts, true));
                                        newOrgnlNtfctnAndSts.appendChild(newRef);
                                    }
                                } catch (Exception e) {
                                    log.error("Error filtering OrgnlItmAndSts: {}", e.getMessage(), e);
                                }
                            });
                });

        if (newOrgnlNtfctnAndSts.hasChildNodes()) {
            ntfctnToRcvStsRpt.appendChild(newOrgnlNtfctnAndSts);
        }

        newDoc.setXmlStandalone(true);
        return newDoc;
    }


    public Boolean validateRequest(ReqPayload request) throws JsonProcessingException {
        Boolean isValid =  request.getHeader().isInvalidPayload();
        if(isValid){
            errorHandling.handleInvalidPayload(request);
        }
        return isValid;
    }
}
