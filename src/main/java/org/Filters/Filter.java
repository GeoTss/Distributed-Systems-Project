package org.Filters;

import java.io.Serializable;

public interface Filter extends Serializable {

    public static enum Types{
        FILTER_STARS,
        FILTER_CATEGORY,
        FILTER_PRICE,
        FILTER_RADIUS,
        END
    }

    boolean satisfy(Object obj);
}
