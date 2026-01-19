package com.arbitragepro.api;

import lombok.Data;

@Data
public class TradeCreateResponse {
    private int trade_id;
    private String status;
    private int item_id;
    private int buy_price;
    private int buy_quantity;
}
