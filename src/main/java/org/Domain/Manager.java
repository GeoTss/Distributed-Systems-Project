package org.Domain;
public class Manager {
    private Credentials managerCredentials;
    private Shop currShop;
    private ShopHandler shopHandler;
    public Manager() {
        this.shopHandler = new ShopHandler();
    }
    public void changeCurrentShop(String _name) {
        this.currShop = this.shopHandler.findShopByName(_name);
    }
    public void addShop(Shop _shop) {
        this.shopHandler.addShop(_shop);
    }
    public void addProduct(Product _product) {
        this.currShop.addProduct(_product);
    }
    public void addAvailableProduct(Product _product, int _amount) {
        this.currShop.getProductByName(_product.getName()).addAvailableAmount(_amount);
    }
    public void removeAvailableProduct(Product _product, int _amount) {
        this.currShop.getProductByName(_product.getName()).removeAvailableAmount(_amount);
    }
    public void removeProduct(Product _product) {
        this.currShop.removeProduct(_product);
    }
    public void getSumSales() {
        this.currShop.getProducts().forEach((_name, _product) -> {
            System.out.println("Number of " + _name + " sold: " + _product.getSold());
        });
    }
}
