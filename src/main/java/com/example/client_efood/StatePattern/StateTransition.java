package com.example.client_efood.StatePattern;

public class StateTransition {
    public final StateInterface nextState;
    public final StateArguments nextArgs;

    public StateTransition(StateInterface nextState, StateArguments nextArgs) {
        this.nextState = nextState;
        this.nextArgs = nextArgs;
    }
}
