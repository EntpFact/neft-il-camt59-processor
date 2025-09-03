package com.hdfcbank.camt59.model;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response {

    String status;
    String message;
}
