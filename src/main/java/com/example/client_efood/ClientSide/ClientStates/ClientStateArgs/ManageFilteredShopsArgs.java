package com.example.client_efood.ClientSide.ClientStates.ClientStateArgs;

import com.example.client_efood.Domain.Shop;
import com.example.client_efood.StatePattern.StateArguments;

import java.util.ArrayList;

public class ManageFilteredShopsArgs implements StateArguments {
    public ArrayList<Shop> filtered_shops = new ArrayList<>();
}
