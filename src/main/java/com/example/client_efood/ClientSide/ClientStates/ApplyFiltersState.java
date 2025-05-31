package com.example.client_efood.ClientSide.ClientStates;

import com.example.client_efood.ClientSide.ClientStates.ClientStateArgs.ApplyFiltersArgs;
import com.example.client_efood.Domain.Utils;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateArguments;
import com.example.client_efood.ClientSide.ClientStates.ClientStateArgs.ManageFilteredShopsArgs;
import com.example.client_efood.Domain.Shop;
import com.example.client_efood.Filters.Filter;
import com.example.client_efood.Filters.PriceCategoryEnum;
import com.example.client_efood.StatePattern.StateTransition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeSet;

public class ApplyFiltersState extends ClientStates {

    ApplyFiltersArgs filters = null;

    @Override
    public StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException {
        System.out.println("ApplyFiltersState.handleState");
        if(arguments != null)
            filters = (ApplyFiltersArgs) arguments;

        handler_info.outputStream.reset();
        handler_info.outputStream.writeInt(MessageType.FILTER.ordinal());

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
            }
        }
        handler_info.outputStream.writeInt(Filter.Types.END.ordinal());

        new Thread(() -> {
            try {

                ArrayList<Shop> filtered_shops;
                synchronized (handler_info.outputStream) {
                    handler_info.outputStream.flush();

                    filtered_shops = (ArrayList<Shop>) handler_info.inputStream.readObject();
                }

                synchronized (handler_info.output_queue){
                    Runnable task = () -> {
                        System.out.println("Received filtered shops!");
                        filtered_shops.forEach(System.out::println);
                    };

                    Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> output_entry = new Utils.Pair<>(
                            task,
                            new Utils.Pair<>(false, null)
                    );
                    handler_info.output_queue.add(output_entry);
                    handler_info.output_queue.notify();
                }

                ManageFilteredShopsArgs args = new ManageFilteredShopsArgs();
                args.filtered_shops = filtered_shops;

                synchronized (handler_info.transition_queue) {
                    handler_info.transition_queue.add(new StateTransition(State.MANAGE_SHOPS.getCorresponding_state(), args));
                    handler_info.transition_queue.notify();
                }
            }catch (ClassNotFoundException | IOException exception){
                exception.printStackTrace();
            }
        }).start();
        return null;
    }

}
