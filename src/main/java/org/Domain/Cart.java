package org.Domain;

import org.Domain.Utils.Pair;

import java.util.ArrayList;

public class Cart {

    private ArrayList<Pair<Product, Integer>> products;

    public void add_product(Product product, int amount){
        products.add(new Pair<>(product, amount));
    }

    public ArrayList<Pair<Product, Integer>> getProducts() {
        return products;
    }
}
