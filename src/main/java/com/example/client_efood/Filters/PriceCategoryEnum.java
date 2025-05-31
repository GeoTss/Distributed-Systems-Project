package com.example.client_efood.Filters;

public enum PriceCategoryEnum {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    private int _price_val;

    public int getPriceVal() {
        return _price_val;
    }

    PriceCategoryEnum(int price_val) {
        _price_val = price_val;
    }

}
