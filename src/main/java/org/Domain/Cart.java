package org.Domain;

import java.io.Serializable;
import java.util.HashMap;

public class Cart implements Serializable {

//    private ArrayList<Pair<Product, Integer>> products;
    private Integer shop_id;
    private HashMap<Integer, Integer> m_products = new HashMap<>();


    public void add_product(Integer product_id, int amount) {
        m_products.put(product_id, amount);
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

    public HashMap<Integer, Integer> getProducts(){
        return m_products;
    }

    public Integer getShop_id() {
        return shop_id;
    }

    public void setShop_id(Integer shop_id) {
        this.shop_id = shop_id;
    }
}
