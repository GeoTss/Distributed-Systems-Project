package com.example.client_efood.MessagePKG;

import com.example.client_efood.Domain.Cart.ServerCart;
import com.example.client_efood.Domain.Product;
import com.example.client_efood.Domain.Shop;

import java.util.ArrayList;
import java.util.HashSet;

public enum MessageArgCast {
    INT_ARG(Integer.class),
    LONG_ARG(Long.class),
    FLOAT_ARG(Float.class),
    DOUBLE_ARG(Double.class),
    STRING_CAST(String.class),
    SHOP_ARG(Shop.class),
    PRODUCT_ARG(Product.class),
    SERVER_CART_ARG(ServerCart.class),
    ARRAY_LIST_ARG(ArrayList.class),
    SET_ARG(HashSet.class);

    private Class<?> type;
    MessageArgCast(Class<?> type){
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCastedArg(Object arg){
        return (T) type.cast(arg);
    }
}