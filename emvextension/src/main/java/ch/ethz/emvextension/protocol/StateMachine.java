package ch.ethz.emvextension.protocol;
public interface StateMachine {
    enum State{
        INIT, SEND_HELLO, RECEIVE_HELLO, RANGE, AUTH_PRE, AUTH, FINISH;
    }
    State getState();
    void step();
    String getStateString();
    State next(State state);
}
