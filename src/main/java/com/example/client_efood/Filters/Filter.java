package com.example.client_efood.Filters;

import java.io.Serializable;

public interface Filter extends Serializable {
    static final long serialVersionUID = 11L;
    public static enum Types{
        FILTER_STARS,
        FILTER_CATEGORY,
        FILTER_PRICE,
        FILTER_RADIUS,
        END
    }

    boolean satisfy(Object obj);
}
