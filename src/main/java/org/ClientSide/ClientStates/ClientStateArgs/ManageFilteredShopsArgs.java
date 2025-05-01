package org.ClientSide.ClientStates.ClientStateArgs;

import org.Domain.Shop;
import org.StatePattern.StateArguments;

import java.util.ArrayList;

public class ManageFilteredShopsArgs implements StateArguments {
    public ArrayList<Shop> filtered_shops = new ArrayList<>();
}
