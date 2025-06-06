package com.example.client_efood.StatePattern;

import java.io.IOException;

public interface StateInterface {

    StateTransition handleState(HandlerInfo info, StateArguments arguments) throws IOException, ClassNotFoundException;
}
