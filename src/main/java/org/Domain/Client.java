package org.Domain;

import org.Domain.Utils.Pair;

import java.util.ArrayList;

public class Client {

    private Credentials userCredentials;
    private Location location;
    private float balance;
    private final ArrayList<Pair<Product, Integer>> shoppingCart;
    private final ArrayList<Pair<Product, Integer>> inventory;
    private final int MAX_RANGE = 5;
    public Client(
            String _username,
            String _password,
            double _latitude,
            double _longitude
    ) {
        this.userCredentials = new Credentials(_username, _password);
        this.location = new Location(_latitude, _longitude);
        this.balance = 0;
        this.shoppingCart = new ArrayList<>();
        this.inventory = new ArrayList<>();
    }

    public double getDistance(Location _otherLoc) {
        return this.location.getDistanceTo(_otherLoc);
    }

    public void addToShoppingCart(Product _product, int _amount) {
        // TODO: check if the current product is being viewed by one or more clients.
        if (_product.getAvailableAmount() - _amount < 0) return;
        Pair<Product, Integer> tempPair = new Pair<>(_product, _amount);
        for (int i = 0; i < this.shoppingCart.size(); i++) {
            Pair<Product, Integer> currPair = this.shoppingCart.get(i);
            if (currPair.first.equals(_product)) {
                this.shoppingCart.set(i, new Pair<>(currPair.first, currPair.second + _amount));
                return;
            }
        }
        this.shoppingCart.add(tempPair);
    }
    public void removeFromShoppingCart(Product _product, int _amount) {
        // TODO: re-add the previously locked items
        Pair<Product, Integer> tempPair = new Pair<>(_product, _amount);
        for (int i = 0; i < this.shoppingCart.size(); i++) {
            Pair<Product, Integer> currPair = this.shoppingCart.get(i);
            if (currPair.first.equals(_product)) {
                if (currPair.second - _amount < 0) return;
                this.shoppingCart.set(i, new Pair<>(currPair.first, currPair.second - _amount));
            }
        }
    }
    public void checkout() {
        float total = (float) this.shoppingCart.stream().mapToDouble(pair -> pair.first.getPrice() * pair.second).sum();
        if (total > this.balance) return;
        this.balance -= total;
        this.shoppingCart.forEach((pair) -> {
            pair.first.sellProduct(pair.second);
            addToInventory(pair.first, pair.second);
        });
        this.shoppingCart.clear();
    }
    public void addToInventory(Product _product, int _amount) {
        Pair<Product, Integer> tempPair = new Pair<>(_product, _amount);
        for (int i = 0; i < this.inventory.size(); i++) {
            Pair<Product, Integer> currPair = this.inventory.get(i);
            if (currPair.first.equals(_product)) {
                inventory.set(i, new Pair<>(currPair.first, currPair.second + _amount));
                return;
            }
        }
        this.inventory.add(tempPair);
    }
    public void setBalance(float _amount) {
        if (_amount < 0) return;
        this.balance += _amount;
    }
    public void rateShop(int _rating, Shop _shop) {
        _shop.updateRating(_rating);
    }
    public String getUsername() {
        return this.userCredentials.getUsername();
    }
    public String getPassword() {
        return this.userCredentials.getPassword();
    }
    public float getBalance() {
        return this.balance;
    }

    public Location getLocation() { return location; }
}