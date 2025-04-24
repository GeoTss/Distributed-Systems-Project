package org.ClientSide.ClientStates;

import org.ClientSide.ClientHandler;
import org.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.Domain.ReadableCart;
import org.Domain.Shop;
import org.ServerSide.Command;

import java.io.IOException;
import java.util.Scanner;

public class ChoseShopState implements ClientState{

    private static void printChoices(){
        System.out.println("0. Go back to home screen.");
        System.out.println("1. Go back to previously viewed shops.");
        System.out.println("2. Checkout.");
        System.out.println("3. Add to cart.");
        System.out.println("4. Remove from cart.");
        System.out.println("5. Show cart.");
    }

    @Override
    public StateTransition handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException {
        System.out.println("ChoseShopState.handleState");
        ChoseShopArgs args = (ChoseShopArgs) arguments;

        handler_info.outputStream.writeInt(Command.CommandTypeClient.CHOSE_SHOP.ordinal());
        handler_info.outputStream.writeInt(args.shop_id);
        handler_info.outputStream.flush();

        Shop resulting_shop;
        try {
            resulting_shop = (Shop) handler_info.inputStream.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        resulting_shop.showProducts();

        printChoices();
        System.out.println("Enter choice:");

        int choice = ClientHandler.sc_input.nextInt();

        while(choice != 0){

            switch (choice){
                case 1 -> {
                    return new StateTransition(State.APPLY_FILTERS, null);
                }
                case 2 -> handleCheckout(handler_info);
                case 3 -> handleAddToCart(handler_info);
                case 4 -> handleRemoveFromCart(handler_info);
                case 5 -> handleShowCart(handler_info);
            }

            printChoices();
            System.out.println("Enter choice:");
            choice = ClientHandler.sc_input.nextInt();
        }

        return new StateTransition(State.INITIAL, null);
    }

    private void handleCheckout(ClientHandlerInfo handler_info) throws IOException {
        handler_info.outputStream.writeInt(Command.CommandTypeClient.CHECKOUT.ordinal());
        handler_info.outputStream.flush();

        boolean checked_out = handler_info.inputStream.readBoolean();
        if (checked_out)
            System.out.println("Checked out successfully.");
        else
            System.out.println("Couldn't checkout. Insufficient funds");
    }

    private void handleAddToCart(ClientHandlerInfo handler_info) throws IOException {
        handler_info.outputStream.writeInt(Command.CommandTypeClient.ADD_TO_CART.ordinal());
        System.out.print("Enter the product ID of the product you want to add: ");
        int product_id = ClientHandler.sc_input.nextInt();

        System.out.print("Enter how many you want to be added: ");
        int quantity = ClientHandler.sc_input.nextInt();

        handler_info.outputStream.writeInt(product_id);
        handler_info.outputStream.writeInt(quantity);
        handler_info.outputStream.flush();

        boolean added_to_cart = handler_info.inputStream.readBoolean();
        if(added_to_cart)
            System.out.println("Added product to cart successfully");
        else
            System.out.println("Product wasn't added to cart successfully.");
    }

    private void handleRemoveFromCart(ClientHandlerInfo handler_info) throws IOException {
        handler_info.outputStream.writeInt(Command.CommandTypeClient.REMOVE_FROM_CART.ordinal());
        System.out.print("Enter product ID of the product you want to remove: ");
        int product_id = ClientHandler.sc_input.nextInt();

        System.out.print("Enter how many you want to be removed: ");
        int quantity = ClientHandler.sc_input.nextInt();

        handler_info.outputStream.writeInt(product_id);
        handler_info.outputStream.writeInt(quantity);
        handler_info.outputStream.flush();

        boolean removed = handler_info.inputStream.readBoolean();
        if(removed)
            System.out.println("Removal was successful.");
    }

    private void handleShowCart(ClientHandlerInfo handler_info) throws IOException {
        handler_info.outputStream.writeInt(Command.CommandTypeClient.GET_CART.ordinal());
        handler_info.outputStream.flush();
        System.out.println("My Cart:");

        ReadableCart readableCart;
        try {
            readableCart = (ReadableCart) handler_info.inputStream.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        System.out.println(readableCart);
    }
}
