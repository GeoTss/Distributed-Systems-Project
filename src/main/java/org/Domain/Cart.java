package org.Domain;

import java.util.ArrayList;

import org.Domain.Utils.Pair;

public class Cart {

    private ArrayList<Pair<Product, Integer>> products;

    public void add_product(Product product, int amount) throws Exception {
        if (product.getAvailableAmount() - amount < 0)
            throw new Exception("Not enough product in stock: " + product.getName() + " " + product.getAvailableAmount()
                    + " available, " + amount + " requested.");
        for (Pair<Product, Integer> pair : products) {
            if (pair.first.equals(product)) {
                int value = pair.second + amount;
                products.set(products.indexOf(pair), new Pair<>(pair.first, value));
                return;
            }
        }
        products.add(new Pair<>(product, amount));
    }

    public void remove_product(Product product, int amount) {
        for (Pair<Product, Integer> pair : products) {
            if (pair.first.equals(product)) {
                int value = pair.second - amount;
                if (value > 0) {
                    products.set(products.indexOf(pair), new Pair<>(pair.first, value));
                } else {
                    products.remove(pair);
                }
                return;
            }
        }
    }

    public float getCartCost() {
        return (float) products.stream().mapToDouble(pair -> pair.first.getPrice() * pair.second).sum();
    }

    public void clear_cart() {
        products.clear();
    }

    public ArrayList<Pair<Product, Integer>> getProducts() {
        return products;
    }
}
