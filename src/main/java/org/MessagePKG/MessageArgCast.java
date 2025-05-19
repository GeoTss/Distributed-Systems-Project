package org.MessagePKG;

import org.Domain.Cart.ServerCart;
import org.Domain.Product;
import org.Domain.Shop;

import java.util.ArrayList;

public enum MessageArgCast {
    INT_ARG(Integer.class),
    LONG_ARG(Long.class),
    FLOAT_ARG(Float.class),
    STRING_CAST(String.class),
    SHOP_ARG(Shop.class),
    PRODUCT_ARG(Product.class),
    SERVER_CART_ARG(ServerCart.class),
    ARRAY_LIST_ARG(ArrayList.class);

    private Class<?> type;
    MessageArgCast(Class<?> type){
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCastedArg(Object arg){
        return (T) type.cast(arg);
    }
}