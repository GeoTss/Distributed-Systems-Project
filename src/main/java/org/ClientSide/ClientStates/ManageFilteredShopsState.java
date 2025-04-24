package org.ClientSide.ClientStates;

import org.ClientSide.ClientHandler;
import org.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.ClientSide.ClientStates.ClientStateArgs.ManageFilteredShopsArgs;

import java.io.IOException;
import java.util.Scanner;

public class ManageFilteredShopsState implements ClientState{

    @Override
    public StateTransition handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException {
        System.out.println("ManageFilteredShopsState.handleState");
        ManageFilteredShopsArgs shop_args = (ManageFilteredShopsArgs) arguments;
        shop_args.filtered_shops.forEach(System.out::println);

        ClientHandler.sc_input = new Scanner(System.in);
        System.out.println("0. Go Back.");
        System.out.println("1. Select shop.");
        System.out.print("Enter choice: ");
        int choice = ClientHandler.sc_input.nextInt();

        if(choice == 0){
            return new StateTransition(State.INITIAL, null);
        } else if (choice == 1) {
            System.out.print("Enter shop id: ");
            int shop_id = ClientHandler.sc_input.nextInt();

            ChoseShopArgs choseShopArgs = new ChoseShopArgs();
            choseShopArgs.shop_id = shop_id;

            return new StateTransition(State.CHOSE_SHOP, choseShopArgs);
        }
        return null;
    }

}
