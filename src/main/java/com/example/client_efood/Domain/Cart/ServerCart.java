package com.example.client_efood.Domain.Cart;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ServerCart implements Serializable {

    private static final long serialVersionUID = 3L;
//    private ArrayList<Pair<Product, Integer>> products;
    private Integer shop_id;
    private Map<Integer, Integer> m_products = new HashMap<>();


    public void add_product(Integer product_id, int amount) {
        Integer prev_quantity = m_products.get(product_id);
        if(prev_quantity == null)
            m_products.put(product_id, amount);
        else
            m_products.put(product_id, amount + prev_quantity);
    }

    public void remove_product(Integer product_id, int amount) {
        Integer current_amount = m_products.get(product_id);
        if (current_amount == null) {
            System.out.println("product_id " + product_id + " doesn't exist in the cart.");
            return;
        }

        int new_amount = current_amount - amount;

        if (new_amount <= 0) {
            m_products.remove(product_id);
            System.out.println("Product " + product_id + " removed from cart");
        } else {
            m_products.put(product_id, new_amount);
            System.out.println("Product " + product_id + " new quantity: " + m_products.get(product_id));
        }
    }


    public void clear_cart() {
        m_products.clear();
    }

    public Map<Integer, Integer> getProducts(){
        return m_products;
    }

    public Integer getShop_id() {
        return shop_id;
    }

    public void setShop_id(Integer shop_id) {
        this.shop_id = shop_id;
    }
}
