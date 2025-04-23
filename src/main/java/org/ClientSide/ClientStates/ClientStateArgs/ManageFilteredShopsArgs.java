package org.ClientSide.ClientStates.ClientStateArgs;

import org.Domain.Shop;

import java.util.ArrayList;

public class ManageFilteredShopsArgs implements ClientStateArgument{
    public ArrayList<Shop> filtered_shops = new ArrayList<>();
}
