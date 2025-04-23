package org.ClientSide.ClientStates;

import org.ClientSide.ClientStates.ClientStateArgs.ChoseShopArgs;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.ClientSide.ClientStates.ClientStateArgs.ManageFilteredShopsArgs;

import java.io.IOException;
import java.util.Scanner;

public class ManageFilteredShopsState implements ClientState{
    @Override
    public void handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException {
        System.out.println("ManageFilteredShopsState.handleState");
        ManageFilteredShopsArgs shop_args = (ManageFilteredShopsArgs) arguments;
        shop_args.filtered_shops.forEach(System.out::println);

        Scanner sc_input = new Scanner(System.in);
        System.out.println("0. Go Back.");
        System.out.println("1. Select shop.");
        System.out.print("Enter choice: ");
        int choice = sc_input.nextInt();

        if(choice == 0){
            ClientState initialState = new InitialState();
            initialState.handleState(handler_info, null);
        } else if (choice == 1) {
            System.out.print("Enter shop id: ");
            int shop_id = sc_input.nextInt();

            ChoseShopArgs choseShopArgs = new ChoseShopArgs();
            choseShopArgs.shop_id = shop_id;
            ClientState choseShopState = new ChoseShopState();
            choseShopState.handleState(handler_info, choseShopArgs);
        }
        sc_input.close();
    }
}
