package com.example.emvextension.protocol;

public interface StateMachine {
    enum State {
        INIT, SELECT, SEND_HELLO, RECEIVE_HELLO, RANGE, CERT, AUTH, FINISH;
    }

    State getState();

    void step();

    String getStateString();

    State next(State state);
}
