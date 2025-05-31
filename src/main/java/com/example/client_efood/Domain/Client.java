package com.example.client_efood.Domain;

import java.io.Serializable;

public class Client implements Serializable {
    private static final long serialVersionUID = 5L;
//    private Credentials userCredentials;
    private String username;
    private Location location;
    private float balance;
    private final int MAX_RANGE = 5;

    public Client(
             String _username,
            // String _password,
            Location _location) {
        username = _username;
        // this.userCredentials = new Credentials(_username, _password);
        this.location = _location;
        this.balance = 0;
    }

    public double getDistance(Location _otherLoc) {
        return this.location.getDistanceTo(_otherLoc);
    }


//    public void checkout() {
//
//        if(!shoppingCart.isValid())
//            return;
//
//        float total = this.shoppingCart.getCartCost();
//        if (total > this.balance) {
//            System.out.println("Not enough money balance: " + this.balance + " total: " + total);
//            return;
//        }
//        this.balance -= total;
//        this.shoppingCart.clear_cart();
//    }

    public void updateBalance(float _amount) {
        if (_amount < 0)
            return;
        this.balance += _amount;
    }

    public void rateShop(int _rating, Shop _shop) {
        _shop.updateRating(_rating);
    }

    public String getUsername() {
        return username;
    }

//    public String getPassword() {
//        return this.userCredentials.getPassword();
//    }

    public float getBalance() {
        return balance;
    }

    public Location getLocation() {
        return location;
    }
}