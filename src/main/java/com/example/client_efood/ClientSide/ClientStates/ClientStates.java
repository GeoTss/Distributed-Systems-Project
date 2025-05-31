package com.example.client_efood.ClientSide.ClientStates;

import com.example.client_efood.StatePattern.StateInterface;

public abstract class ClientStates implements StateInterface {
    public enum State{
        INITIAL(new InitialState()),
        APPLY_FILTERS(new ApplyFiltersState()),
        MANAGE_SHOPS(new ManageFilteredShopsState()),
        CHOSE_SHOP(new ChoseShopState());

        private StateInterface corresponding_state;

        State(StateInterface correspondingState) {
            corresponding_state = correspondingState;
        }

        public StateInterface getCorresponding_state(){
            return corresponding_state;
        }
    };
}
