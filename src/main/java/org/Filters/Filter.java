package org.Filters;

import java.io.Serializable;

public interface Filter extends Serializable {

    public static enum Types{
        FILTER_STARS, // FILTER_STARS stars_amount
        FILTER_CATEGORY, // FILTER_CATEGORY category
        FILTER_PRICE, // FILTER_PRICE price_category
        FILTER_RADIUS, // FILTER_RADIUS max_radius
        END
    }

    boolean satisfy(Object obj);
}
