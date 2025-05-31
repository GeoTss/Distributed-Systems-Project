package com.example.client_efood.Domain.Cart;

import com.example.client_efood.Domain.Product;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ReadableCart implements Serializable {

    private static final long serialVersionUID = 2L;

    private CartStatus server_sync_status;
    private Map<Product, Integer> product_quantity_map = new HashMap<>();
    private Float total_cost;

    public Map<Product, Integer> getProduct_quantity_map() {
        return product_quantity_map;
    }

    public Float getTotal_cost() {
        return total_cost;
    }

    public void setTotal_cost(Float total_cost) {
        this.total_cost = total_cost;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Product, Integer> entry : product_quantity_map.entrySet()) {
            Product product = entry.getKey();
            Integer quantity = entry.getValue();
            float lineTotal = product.getPrice() * quantity;

            sb.append(String.format("  %d  - %s (%s): %d @ %.2f€ each [%.2f€ total]\n",
                    product.getId(),
                    product.getName(),
                    product.getType(),
                    quantity,
                    product.getPrice(),
                    lineTotal));
        }

        sb.append(String.format("Total Cost: %.2f€\n", total_cost));

        return sb.toString();
    }

    public CartStatus getServer_sync_status() {
        return server_sync_status;
    }

    public void setServer_sync_status(CartStatus server_sync_status) {
        this.server_sync_status = server_sync_status;
    }
}
