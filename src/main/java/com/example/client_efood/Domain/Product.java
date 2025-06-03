package com.example.client_efood.Domain;

import java.io.Serializable;

public class Product implements Serializable {

    private static final long serialVersionUID = 8L;

    private static Integer gl_id = 0;

    private Integer id;
    final private String name;
    final private String type;
    private int availableAmount;
    final private float price;
    private int sold;

    private boolean is_removed;

    public Product(
        String _name,
        String _type,
        int _availableAmount,
        float _price
    ) {
        id = gl_id++;

        this.name = _name;
        this.type = _type;
        this.availableAmount = _availableAmount;
        this.price = _price;
        this.sold = 0;

        is_removed = false;
    }

    public Product(Product otherProduct) {
        if (otherProduct == null) {
            this.id = -1;
            this.name = "Unknown";
            this.type = "Unknown";
            this.availableAmount = 0;
            this.price = 0.0f;
            this.sold = 0;
            this.is_removed = true;
            return;
        }

        this.id = otherProduct.id;
        this.name = otherProduct.name;
        this.type = otherProduct.type;
        this.availableAmount = otherProduct.availableAmount;
        this.price = otherProduct.price;
        this.sold = otherProduct.sold;
        this.is_removed = otherProduct.is_removed;
    }

    public void addAvailableAmount(int _amount) {
        if (_amount < 0) return;
        this.availableAmount += _amount;
    }
    public void removeAvailableAmount(int _amount) {
        this.availableAmount = Math.max(this.availableAmount - _amount, 0);
    }
    public void sellProduct(int _numSold) {
        this.sold += _numSold;
    }
    public String getName() {
        return this.name;
    }
    public String getType() {
        return this.type;
    }
    public int getAvailableAmount() {
        return this.availableAmount;
    }
    public float getPrice() {
        return this.price;
    }
    public int getSold() {
        return this.sold;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Product with ID: ").append(id).append("\n")
                .append("\t- ").append(name)
                .append(" (").append(type).append("): ")
                .append(availableAmount).append(" available @ ")
                .append(price).append("€\n");
        return sb.toString();
    }

    @Override
    public int hashCode(){
        return id;
    }

    public Integer getId() {
        return id;
    }

    public void set_removed_status(boolean is_removed) {
        this.is_removed = is_removed;
    }

    public boolean is_removed() {
        return is_removed;
    }
}