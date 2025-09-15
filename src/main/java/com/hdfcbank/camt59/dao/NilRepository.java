package com.hdfcbank.camt59.dao;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.camt59.model.MsgEventTracker;
import com.hdfcbank.camt59.model.TransactionAudit;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@EnableCaching
public class NilRepository {

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public String findTargetByTxnId(String txnId) {
        String sql = "SELECT target FROM network_il.transaction_audit WHERE txn_id = :txnId";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("txnId", txnId);

        try {
            return namedParameterJdbcTemplate.queryForObject(sql, params, String.class);
        } catch (Exception e) {
            // log and return null or handle accordingly
            return null;
        }

    }

    public void saveDataInMsgEventTracker(MsgEventTracker msgEventTracker) throws JsonProcessingException, SQLException {
        String sql = "INSERT INTO network_il.msg_event_tracker\n" +
                "(msg_id, \"source\", target, batch_id, flow_type, msg_type, original_req, invalid_msg, \n" +
                " replay_count, original_req_count, consolidate_amt,transformed_json_req, intermediate_req, intemdiate_count, \n" +
                " status, batch_creation_date, batch_timestamp, created_time, modified_timestamp, \"version\")\n" +
                "VALUES" +
                " (:msg_id, :source, :target,:batch_id, :flow_type, :msg_type, :original_req, :invalid_msg," +
                " :replay_count, :original_req_count, :consolidate_amt,:transformed_json_req,  :intermediate_req, :intemdiate_count," +
                " :status,:batch_creation_date,:batch_timestamp, :created_time, :modified_timestamp,:version )";

        LocalDateTime timestamp = LocalDateTime.now();


        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("msg_id", msgEventTracker.getMsgId());
        params.addValue("source", msgEventTracker.getSource());
        params.addValue("target", msgEventTracker.getTarget());
        params.addValue("flow_type", msgEventTracker.getFlowType());
        params.addValue("msg_type", msgEventTracker.getMsgType());
        params.addValue("original_req", msgEventTracker.getOrgnlReq());
        params.addValue("original_req_count", msgEventTracker.getOrgnlReqCount());
        params.addValue("batch_id", " ");
        params.addValue("version",1);
        params.addValue("invalid_msg",msgEventTracker.isInvalidPayload());
        params.addValue("replay_count",0);
        params.addValue("consolidate_amt", msgEventTracker.getConsolidateAmt());
        params.addValue("intermediate_req", msgEventTracker.getIntermediateReq());
        params.addValue("intemdiate_count", msgEventTracker.getIntermediateCount());
        params.addValue("status", msgEventTracker.getStatus());
        params.addValue("batch_creation_date", msgEventTracker.getBatchCreationDate());
        params.addValue("batch_timestamp", msgEventTracker.getBatchCreationTime());
        params.addValue("version",1);
        params.addValue("created_time", timestamp);
        params.addValue("modified_timestamp", timestamp);

        ObjectMapper objectMapper = new ObjectMapper();

//        try {
        // 1. Convert the ReqPayload object to JSON string
        String jsonString = objectMapper.writeValueAsString(msgEventTracker.getTransformedJsonReq());

        // 2. Wrap the string as a PGobject
        PGobject jsonObject = new PGobject();
        jsonObject.setType("json"); // or "jsonb" if your column is JSONB
        jsonObject.setValue(jsonString);
//            params.addValue("transformed_json_req",objectMapper.writeValueAsString(msgEventTracker.getTransformedJsonReq()));
        // 3. Add to parameters
        params.addValue("transformed_json_req", jsonObject);

        namedParameterJdbcTemplate.update(sql, params);

    }
//
//    public MsgEventTracker findByMsgId(String msgId) {
//        String sql = "SELECT * FROM network_il.msg_event_tracker WHERE msg_id = :msgId";
//
//        MapSqlParameterSource params = new MapSqlParameterSource();
//        params.addValue("msgId", msgId);
//
//        List<MsgEventTracker> result = namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
//            MsgEventTracker tracker = new MsgEventTracker();
//            tracker.setMsgId(rs.getString("msg_id"));
//            tracker.setSource(rs.getString("source"));
//            tracker.setTarget(rs.getString("target"));
//            tracker.setFlowType(rs.getString("flow_type"));
//            tracker.setMsgType(rs.getString("msg_type"));
//            tracker.setOrgnlReq(rs.getString("original_req"));
//            tracker.setOrgnlReqCount(rs.getInt("original_req_count"));
//            tracker.setConsolidateAmt(rs.getBigDecimal("consolidate_amt"));
//            tracker.setIntermediateReq(rs.getString("intermediate_req"));
//            tracker.setIntermediateCount(rs.getInt("intemdiate_count"));
//            tracker.setStatus(rs.getString("status"));
//            tracker.setCreatedTime(rs.getObject("created_time", LocalDateTime.class));
//            tracker.setModifiedTimestamp(rs.getObject("modified_timestamp", LocalDateTime.class));
//            return tracker;
//        });
//
//        return result.isEmpty() ? null : result.get(0);
//    }
//
//    public void saveDuplicateEntry(MsgEventTracker tracker) {
//        String selectSql = "SELECT MAX(version) FROM network_il.msg_dedup_tracker " +
//                "WHERE msg_id = :msgId";
//
//        MapSqlParameterSource baseParams = new MapSqlParameterSource();
//        baseParams.addValue("msgId", tracker.getMsgId());
//
//
//        BigDecimal currentVersion = namedParameterJdbcTemplate.queryForObject(
//                selectSql, baseParams, BigDecimal.class);
//
//        if (currentVersion != null) {
//            // Row exists → update version
//            BigDecimal nextVersion = currentVersion.add(BigDecimal.ONE);
//
//            String updateSql = "UPDATE network_il.msg_dedup_tracker SET " +
//                    "flow_type = :flowType, msg_type = :msgType, original_req = (XMLPARSE(CONTENT :originalReq)), " +
//                    "version = :version, modified_timestamp = CURRENT_TIMESTAMP " +
//                    "WHERE msg_id = :msgId AND source = :source AND target = :target";
//
//            MapSqlParameterSource updateParams = new MapSqlParameterSource();
//            updateParams.addValue("msgId", tracker.getMsgId());
//            updateParams.addValue("source", tracker.getSource());
//            updateParams.addValue("target", tracker.getTarget());
//            updateParams.addValue("flowType", tracker.getFlowType());
//            updateParams.addValue("msgType", tracker.getMsgType());
//            updateParams.addValue("originalReq", tracker.getOrgnlReq());
//            updateParams.addValue("version", nextVersion);
//
//            namedParameterJdbcTemplate.update(updateSql, updateParams);
//
//        } else {
//            // Row does not exist → insert with version = 1
//            String insertSql = "INSERT INTO network_il.msg_dedup_tracker " +
//                    "(msg_id, source, target, flow_type, msg_type, original_req, version, created_time, modified_timestamp) " +
//                    "VALUES (:msgId, :source, :target, :flowType, :msgType, (XMLPARSE(CONTENT :originalReq)), 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
//
//            MapSqlParameterSource insertParams = new MapSqlParameterSource();
//            insertParams.addValue("msgId", tracker.getMsgId());
//            insertParams.addValue("source", tracker.getSource());
//            insertParams.addValue("target", tracker.getTarget());
//            insertParams.addValue("flowType", tracker.getFlowType());
//            insertParams.addValue("msgType", tracker.getMsgType());
//            insertParams.addValue("originalReq", tracker.getOrgnlReq());
//
//            namedParameterJdbcTemplate.update(insertSql, insertParams);
//        }
//    }


    public void saveAllTransactionAudits(List<TransactionAudit> transactionAudits) {
        String sql = "INSERT INTO network_il.transaction_audit (" +
                "msg_id, txn_id, end_to_end_id,batch_id, return_id,  source, target, " +
                "flow_type, msg_type, amount, status,version, batch_creation_date,batch_timestamp ,created_time, modified_timestamp) " +
                "VALUES (:msg_id, :txn_id, :end_to_end_id, :batch_id,:return_id, " +
                ":source, :target, :flow_type, :msg_type, :amount,:status, :version,:batch_creation_date,:batch_timestamp, :created_time, :modified_timestamp)";

        LocalDateTime timestamp = LocalDateTime.now();
        LocalDate date=LocalDate.now();
        List<MapSqlParameterSource> batchParams = transactionAudits.stream()
                .map(tx -> {
                    MapSqlParameterSource params = new MapSqlParameterSource();
                    params.addValue("batch_id", " ");
                    params.addValue("msg_id", tx.getMsgId());
                    params.addValue("txn_id", tx.getTxnId());
                    params.addValue("end_to_end_id", tx.getEndToEndId());
                    params.addValue("return_id", tx.getReturnId());
                    params.addValue("req_payload", tx.getReqPayload());
                    params.addValue("source", tx.getSource());
                    params.addValue("target", tx.getTarget());
                    params.addValue("flow_type", tx.getFlowType());
                    params.addValue("msg_type", tx.getMsgType());
                    params.addValue("amount", tx.getAmount());
                    params.addValue("status","INPROGRESS");
                   // params.addValue("batch_id", tx.getBatchId());
                    params.addValue("version",1);
                    params.addValue("created_time", timestamp);
                    params.addValue("batch_creation_date",tx.getBatchDate());
                    params.addValue("batch_timestamp",tx.getBatchTime());
                    params.addValue("modified_timestamp", timestamp);
                    return params;
                })
                .toList();

        namedParameterJdbcTemplate.batchUpdate(sql, batchParams.toArray(new MapSqlParameterSource[0]));
    }

}
