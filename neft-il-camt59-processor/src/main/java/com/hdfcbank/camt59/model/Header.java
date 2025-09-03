package com.hdfcbank.camt59.model;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
public class Header {
    private String msgId;//
    private String source;//
    private String target;
    private String flowType;//
    private boolean replayInd;
    private boolean invalidPayload;
    private String prefix;
    private String status;
    private String msgType;


}
