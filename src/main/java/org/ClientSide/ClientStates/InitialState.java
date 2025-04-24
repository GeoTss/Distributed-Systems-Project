package org.ClientSide.ClientStates;

import org.ClientSide.ClientHandler;
import org.ClientSide.ClientStates.ClientStateArgs.ApplyFiltersArgs;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.Filters.Filter;
import org.Filters.PriceCategoryEnum;

import java.io.IOException;
import java.util.*;

public class InitialState implements ClientState{

    @Override
    public StateTransition handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException {
        System.out.println("InitialState.handleState");

        int command;
        do {
            System.out.println("0. Quit.");
            System.out.println("1. Enter filters.");
            System.out.print("Enter command: ");
            command = ClientHandler.sc_input.nextInt();

            if(command == 1) {
                ApplyFiltersArgs filter_arguments = new ApplyFiltersArgs();

                Filter.Types corresponding_type = null;
                do{
                    System.out.println(Filter.Types.END.ordinal() + ". To stop adding filters.");
                    System.out.println(Filter.Types.FILTER_STARS.ordinal() + ". Filter by stars.");
                    System.out.println(Filter.Types.FILTER_CATEGORY.ordinal() + ". Filter by categories.");
                    System.out.println(Filter.Types.FILTER_PRICE.ordinal() + ". Filter by price.");
                    System.out.println(Filter.Types.FILTER_RADIUS.ordinal() + ". Filter by radius.");
                    System.out.print("What filter do you want? ");
                    corresponding_type = Filter.Types.values()[ClientHandler.sc_input.nextInt()];

                    switch (corresponding_type){
                        case FILTER_STARS -> {
                            System.out.print("Enter minimum rating for a store to have (0-5): ");
                            Float min_rating = ClientHandler.sc_input.nextFloat();

                            filter_arguments.filter_types.add(Filter.Types.FILTER_STARS);
                            filter_arguments.additional_filter_args.put(Filter.Types.FILTER_STARS, min_rating);
                        }
                        case FILTER_CATEGORY -> {
                            Set<String> categories = new TreeSet<>();
                            String category = "";
                            System.out.print("Enter category for filtering: ");
                            category = ClientHandler.sc_input.next();

                            while(!category.equals("EOF")) {
                                categories.add(category);
                                System.out.print("Enter category for filtering: ");
                                category = ClientHandler.sc_input.next();
                            }

                            filter_arguments.filter_types.add(Filter.Types.FILTER_CATEGORY);
                            filter_arguments.additional_filter_args.put(Filter.Types.FILTER_CATEGORY, categories);
                        }
                        case FILTER_PRICE -> {
                            System.out.println(PriceCategoryEnum.LOW.ordinal() + ". For low price.");
                            System.out.println(PriceCategoryEnum.MEDIUM.ordinal() + ". For medium price.");
                            System.out.println(PriceCategoryEnum.HIGH.ordinal() + ". For high price.");
                            System.out.print("Enter the price category you are looking for:");

                            PriceCategoryEnum pr_cat = PriceCategoryEnum.values()[ClientHandler.sc_input.nextInt()];
                            filter_arguments.filter_types.add(Filter.Types.FILTER_PRICE);
                            filter_arguments.additional_filter_args.put(Filter.Types.FILTER_PRICE, pr_cat);
                        }
                        case FILTER_RADIUS -> {
                            System.out.print("Enter maximum distance radius:");
                            Double radius = ClientHandler.sc_input.nextDouble();

                            filter_arguments.filter_types.add(Filter.Types.FILTER_RADIUS);
                            filter_arguments.additional_filter_args.put(Filter.Types.FILTER_RADIUS, radius);
                        }
                    }

                }while(corresponding_type != Filter.Types.END);

                filter_arguments.filter_types.add(Filter.Types.END);

                return new StateTransition(State.APPLY_FILTERS, filter_arguments);
            }

        }while(command != 0);

        return null;
    }

}
