package org.ClientSide.ClientStates;

import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;

import java.io.IOException;

public interface ClientState {

    enum State{
        INITIAL(new InitialState()),
        APPLY_FILTERS(new ApplyFiltersState()),
        MANAGE_SHOPS(new ManageFilteredShopsState()),
        CHOSE_SHOP(new ChoseShopState());

        private ClientState corresponding_state;

        State(ClientState correspondingState) {
            corresponding_state = correspondingState;
        }

        public ClientState getCorresponding_state(){
            return corresponding_state;
        }
    };

    StateTransition handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException, ClassNotFoundException;
}
