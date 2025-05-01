package org.StatePattern;

import java.io.IOException;

public interface StateInterface {

    StateTransition handleState(HandlerInfo handler_info, StateArguments arguments) throws IOException, ClassNotFoundException;
}
