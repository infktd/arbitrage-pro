package com.arbitragepro.api;

import lombok.Data;

@Data
public class ActiveTrade {
    private int trade_id;
    private int item_id;
    private String item_name;
    private int buy_price;
    private int sell_price;
    private int buy_quantity;
    private String status;  // "buying", "bought", "selling", "completed"
    private int buy_quantity_filled;
    private int sell_quantity_filled;
}
