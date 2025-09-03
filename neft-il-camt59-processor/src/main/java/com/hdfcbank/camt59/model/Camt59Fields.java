package com.hdfcbank.camt59.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class Camt59Fields {
    String bizMsgIdr;
    String endToEndId;
    String txId;
    String amount;
    String swtch;
}
