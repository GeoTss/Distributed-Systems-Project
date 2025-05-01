package org.ManagerSide.ManagerStates;

import org.ManagerSide.ManagerHandler;
import org.Domain.Shop;
import org.ManagerSide.ManagerStates.ManagerStateArgs.ChoseShopArgs;
import org.ManagerSide.ManagerStates.ManagerStateArgs.ManagerStateArgument;
import org.ServerSide.Command;
import org.StatePattern.HandlerInfo;
import org.StatePattern.StateArguments;
import org.StatePattern.StateTransition;

import java.io.IOException;

public class ChoseShopState extends ManagerState {

    private static void printChoices(){
        System.out.println("0. Go Back.");
        System.out.println("1. Add Product.");
        System.out.println("2. Remove Product.");
        System.out.println("3. Add Available Product.");
        System.out.println("4. Remove Available Product.");
    }

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException {
        System.out.println("ChoseShopState.handleState");
        ChoseShopArgs args = (ChoseShopArgs) arguments;

        handler_info.outputStream.writeInt(Command.CommandTypeManager.CHOSE_SHOP.ordinal());
        handler_info.outputStream.flush();
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
        System.out.print("Enter choice: ");

        int choice = ManagerHandler.sc_input.nextInt();

        while(choice != 0){

            switch (choice){
                case 1 -> handleAddProduct(handler_info);
                case 2 -> handleRemoveProduct(handler_info);
                case 3 -> handleAddAvailableProduct(handler_info);
                case 4 -> handleRemoveAvailableProduct(handler_info);
            }

            printChoices();
            System.out.println("Enter choice:");
            choice = ManagerHandler.sc_input.nextInt();
        }

        return new StateTransition(State.INITIAL.getCorresponding_state(), null);
    }

    private void handleAddProduct(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        handler_info.outputStream.writeInt(Command.CommandTypeManager.ADD_PRODUCT.ordinal());

        System.out.println("Give product name: ");
        String name = ManagerHandler.sc_input.next();

        System.out.println("Give product type: ");
        String type = ManagerHandler.sc_input.next();

        System.out.println("Give product available amount: ");
        int available_amount = ManagerHandler.sc_input.nextInt();

        System.out.println("Give product price: ");
        float price = ManagerHandler.sc_input.nextFloat();

        handler_info.outputStream.writeUTF(name);
        handler_info.outputStream.writeUTF(type);
        handler_info.outputStream.writeInt(available_amount);
        handler_info.outputStream.writeFloat(price);
        handler_info.outputStream.flush();

        boolean successfully_added = handler_info.inputStream.readBoolean();
        if (successfully_added) {
            System.out.println("Product was successfully added.");
        } else {
            System.out.println("Failure on adding the product, aborting.");
        }

    }

    private void handleRemoveProduct(HandlerInfo handler_info) throws IOException, ClassNotFoundException {
        handler_info.outputStream.writeInt(Command.CommandTypeManager.REMOVE_PRODUCT.ordinal());
        handler_info.outputStream.flush();

        System.out.print("Enter the product ID of the product you want to remove: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        handler_info.outputStream.writeInt(product_id);
        handler_info.outputStream.flush();

        boolean successfully_removed = handler_info.inputStream.readBoolean();
        if (successfully_removed) {
            System.out.println("Product was successfully removed.");
        } else {
            System.out.println("Failure in removing the product, aborting.");
        }
    }

    private void handleAddAvailableProduct(HandlerInfo handler_info) throws IOException {
        handler_info.outputStream.writeInt(Command.CommandTypeManager.ADD_AVAILABLE_PRODUCT.ordinal());
        System.out.print("Enter the product ID of the product you want to add: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        System.out.print("Enter the quantity to be added. ");
        int quantity = ManagerHandler.sc_input.nextInt();

        handler_info.outputStream.writeInt(product_id);
        handler_info.outputStream.writeInt(quantity);
        handler_info.outputStream.flush();

        boolean successfully_added = handler_info.inputStream.readBoolean();
        if(successfully_added)
            System.out.println("Added the requested quantity to the product, successfully.");
        else
            System.out.println("Failed in adding the amount requested, aborting.");
    }

    private void handleRemoveAvailableProduct(HandlerInfo handler_info) throws IOException, ClassNotFoundException {

        System.out.println("Enter the product ID of the product you want to remove: ");
        int product_id = ManagerHandler.sc_input.nextInt();

        System.out.println("Enter the quantity to be removed.");
        int quantity = ManagerHandler.sc_input.nextInt();

        handler_info.outputStream.writeInt(Command.CommandTypeManager.REMOVE_AVAILABLE_PRODUCT.ordinal());
        handler_info.outputStream.writeInt(product_id);
        handler_info.outputStream.writeInt(quantity);
        handler_info.outputStream.flush();

        boolean successfully_removed = handler_info.inputStream.readBoolean();
        if (successfully_removed) {
            System.out.println("Product was successfully removed.");
        } else {
            System.out.println("Failure in removing the amount requested, aborting.");
        }
    }

}
