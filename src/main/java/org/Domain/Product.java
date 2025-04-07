package org.Domain;

import java.io.Serializable;

public class Product implements Serializable {
    final private String name;
    final private String type;
    private int availableAmount;
    final private float price;
    private int sold;
    public Product(
        String _name,
        String _type,
        int _availableAmount,
        float _price
    ) {
        this.name = _name;
        this.type = _type;
        this.availableAmount = _availableAmount;
        this.price = _price;
        this.sold = 0;
    }
    public void addAvailableAmount(int _amount) {
        if (_amount < 0) return;
        this.availableAmount += _amount;
    }
    public void removeAvailableAmount(int _amount) {
        this.availableAmount = Math.max(this.availableAmount - _amount, 0);
    }
    public void sellProduct(int _numSold) {
        this.availableAmount -= _numSold;
        this.sold += _numSold;
    }
    public float getSumSale() {
        return this.sold * this.price; 
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

}