package ch.ethz.emvextension.protocol;


import static ch.ethz.emvextension.protocol.StateMachine.State.AUTH;
import static ch.ethz.emvextension.protocol.StateMachine.State.FINISH;
import static ch.ethz.emvextension.protocol.StateMachine.State.INIT;
import static ch.ethz.emvextension.protocol.StateMachine.State.RANGE;
import static ch.ethz.emvextension.protocol.StateMachine.State.RECEIVE_HELLO;
import static ch.ethz.emvextension.protocol.StateMachine.State.SEND_HELLO;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CardStateMachine implements StateMachine {
    private State state;
    private static final Map<State, State> steps;

    static {
        Map<State, State> steps_tmp = new HashMap<State, State>();
        steps_tmp.put(INIT, RECEIVE_HELLO);
        steps_tmp.put(RECEIVE_HELLO, SEND_HELLO);
        steps_tmp.put(SEND_HELLO, RANGE);
        steps_tmp.put(RANGE, AUTH);
        steps_tmp.put(AUTH, FINISH);

        steps = Collections.unmodifiableMap(steps_tmp);
    }

    public CardStateMachine() {
        this.state = INIT;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void step() {
        state = steps.get(this.state);
    }

    @Override
    public String getStateString() {
        return StateMachineUtils.stateToString(state);
    }

    @Override
    public State next(State state) {
        return steps.get(state);
    }
}
