package com.arbitragepro.api;

import lombok.Data;

@Data
public class TradeUpdateResponse {
    private boolean success;
    private ActiveTrade trade;
    private String action;  // "wait", "sell", "complete"
    private Integer sell_price;
}
