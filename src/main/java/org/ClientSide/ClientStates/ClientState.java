package org.ClientSide.ClientStates;

import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;

import java.io.IOException;

public interface ClientState {
    void handleState(ClientHandlerInfo handler_info, ClientStateArgument arguments) throws IOException;
}
