package org.ClientSide.ClientStates;

import org.ClientSide.ClientStates.ClientStateArgs.ApplyFiltersArgs;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.ClientSide.ClientStates.ClientStateArgs.ManageFilteredShopsArgs;
import org.Domain.Shop;
import org.Filters.Filter;
import org.Filters.PriceCategoryEnum;
import org.ServerSide.Command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeSet;

public class ApplyFiltersState implements ClientState{

    ApplyFiltersArgs filters = null;

    @Override
    public void handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException {
        System.out.println("ApplyFiltersState.handleState");
        if(arguments != null) {
            filters = (ApplyFiltersArgs) arguments;
        }
        handler_info.outputStream.writeInt(Command.CommandTypeClient.FILTER.ordinal());

        for(Filter.Types filter_type: filters.filter_types){
            switch (filter_type){
                case FILTER_STARS -> {
                    handler_info.outputStream.writeInt(Filter.Types.FILTER_STARS.ordinal());
                    Float min_rating = (Float) filters.additional_filter_args.get(filter_type);
                    handler_info.outputStream.writeFloat(min_rating);
                }
                case FILTER_CATEGORY -> {
                    handler_info.outputStream.writeInt(Filter.Types.FILTER_CATEGORY.ordinal());
                    @SuppressWarnings("unchecked")
                    TreeSet<String> categories = (TreeSet<String>) filters.additional_filter_args.get(filter_type);
                    handler_info.outputStream.writeObject(categories);
                }
                case FILTER_PRICE -> {
                    handler_info.outputStream.writeInt(Filter.Types.FILTER_PRICE.ordinal());
                    PriceCategoryEnum pr_cat = (PriceCategoryEnum) filters.additional_filter_args.get(filter_type);
                    handler_info.outputStream.writeInt(pr_cat.ordinal());
                }
                case FILTER_RADIUS -> {
                    handler_info.outputStream.writeInt(Filter.Types.FILTER_RADIUS.ordinal());
                    Double max_radius = (Double) filters.additional_filter_args.get(filter_type);
                    handler_info.outputStream.writeDouble(max_radius);
                }
                case END -> {
                    handler_info.outputStream.writeInt(Filter.Types.END.ordinal());
                }
            }
        }
        handler_info.outputStream.flush();

        try {
            @SuppressWarnings("unchecked")
            ArrayList<Shop> filtered_shops = (ArrayList<Shop>) handler_info.inputStream.readObject();

            ClientState manage_shops = new ManageFilteredShopsState();
            ManageFilteredShopsArgs args = new ManageFilteredShopsArgs();
            args.filtered_shops = filtered_shops;
            manage_shops.handleState(handler_info, args);
        }catch (ClassNotFoundException exception){
            exception.printStackTrace();
        }
    }
}
