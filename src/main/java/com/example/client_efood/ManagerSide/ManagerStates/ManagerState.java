package com.example.client_efood.ManagerSide.ManagerStates;

import com.example.client_efood.StatePattern.StateInterface;

public abstract class ManagerState implements StateInterface {

        public enum State{
            INITIAL(new InitialState()),
            CHOSE_SHOP(new ChoseShopState()), // Will have the add/remove (available) product.
            DISPLAY_TOTAL_SALES(new DisplayTotalSalesState());

            private StateInterface corresponding_state;

            State(StateInterface correspondingState) {
                corresponding_state = correspondingState;
            }

            public StateInterface getCorresponding_state(){
                return corresponding_state;
            }
        };
}

