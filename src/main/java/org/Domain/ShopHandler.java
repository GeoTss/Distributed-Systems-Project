package org.Domain;
import java.util.HashMap;
class ShopHandler {
    private HashMap<String, Shop> shops;

    public ShopHandler() {
        this.shops = new HashMap<String, Shop>();
    }

    public HashMap<String, Shop> getShopsInRange(Client _currClient, float _max_range) {
        HashMap<String, Shop> temp = new HashMap<>();
        this.shops.forEach((_name, _shop) -> {
            if (_currClient.getLocation().isInRange(_shop.getLocation(), _max_range)) {
                temp.put(_name, _shop);
            }
        });
        return temp;
    }

    public Shop findShopByName(String _name) {
        return this.shops.get(_name);
    }
    public void addShop(Shop _shop) {
        this.shops.put(_shop.getName(), _shop);
    }
    public HashMap<String, Shop> getShops() {
        return this.shops;
    }

}