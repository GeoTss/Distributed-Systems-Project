package org.ClientSide.ClientStates;

import org.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.Domain.Product;
import org.Domain.ReadableCart;
import org.Domain.ServerCart;
import org.Domain.Shop;
import org.ServerSide.Command;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

public class ChoseShopState implements ClientState{

    private static void printChoices(){
        System.out.println("0. Go back.");
        System.out.println("1. Checkout.");
        System.out.println("2. Add to cart.");
        System.out.println("3. Remove from cart.");
        System.out.println("4. Show cart.");
    }

    @Override
    public void handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException {
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

        Scanner sc_input = new Scanner(System.in);
        int choice = sc_input.nextInt();

        while(true){

            switch (choice){
                case 0 -> {
                    ClientState apply_filters_state = new ApplyFiltersState();
                    apply_filters_state.handleState(handler_info, null);
                }
                case 1 -> {
                    handler_info.outputStream.writeInt(Command.CommandTypeClient.CHECKOUT.ordinal());
                    handler_info.outputStream.flush();

                    boolean checked_out = handler_info.inputStream.readBoolean();
                    if (checked_out)
                        System.out.println("Checked out successfully.");
                    else
                        System.out.println("Couldn't checkout. Insufficient funds");
                }
                case 2 -> {
                    handler_info.outputStream.writeInt(Command.CommandTypeClient.ADD_TO_CART.ordinal());
                    System.out.print("Enter the product ID of the product you want to add: ");
                    int product_id = sc_input.nextInt();

                    System.out.print("Enter how many you want to be added: ");
                    int quantity = sc_input.nextInt();

                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.writeInt(quantity);
                    handler_info.outputStream.flush();

                    boolean added_to_cart = handler_info.inputStream.readBoolean();
                    if(added_to_cart)
                        System.out.println("Added product to cart successfully");
                    else
                        System.out.println("Product wasn't added to cart successfully.");
                }
                case 3 -> {
                    handler_info.outputStream.writeInt(Command.CommandTypeClient.REMOVE_FROM_CART.ordinal());
                    System.out.print("Enter product ID of the product you want to remove: ");
                    int product_id = sc_input.nextInt();

                    System.out.print("Enter how many you want to be removed: ");
                    int quantity = sc_input.nextInt();

                    handler_info.outputStream.writeInt(product_id);
                    handler_info.outputStream.writeInt(quantity);
                    handler_info.outputStream.flush();

                    boolean removed = handler_info.inputStream.readBoolean();
                    if(removed)
                        System.out.println("Removal was successful.");
                }
                case 4 -> {
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

            printChoices();
            System.out.println("Enter choice:");
            choice = sc_input.nextInt();
        }
    }
}
