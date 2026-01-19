package com.arbitragepro.api;

import lombok.Data;

@Data
public class Recommendation {
    private int item_id;
    private String item_name;
    private int buy_price;
    private int sell_price;
    private int buy_quantity;
    private int margin;
    private int ge_limit;
    private double ml_score;
    private int expected_profit;
    private double expected_roi_percent;
    private int volume_24h;
}
