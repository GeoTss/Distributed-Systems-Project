package org.ClientSide.ClientStates;

import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;

public class StateTransition {
    public final ClientState.State nextState;
    public final ClientStateArgument nextArgs;

    public StateTransition(ClientState.State nextState, ClientStateArgument nextArgs) {
        this.nextState = nextState;
        this.nextArgs = nextArgs;
    }
}
