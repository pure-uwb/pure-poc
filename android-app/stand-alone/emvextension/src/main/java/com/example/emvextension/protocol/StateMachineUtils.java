package com.example.emvextension.protocol;

import static com.example.emvextension.protocol.StateMachine.State.AUTH;
import static com.example.emvextension.protocol.StateMachine.State.CERT;
import static com.example.emvextension.protocol.StateMachine.State.FINISH;
import static com.example.emvextension.protocol.StateMachine.State.INIT;
import static com.example.emvextension.protocol.StateMachine.State.RANGE;
import static com.example.emvextension.protocol.StateMachine.State.RECEIVE_HELLO;
import static com.example.emvextension.protocol.StateMachine.State.SELECT;
import static com.example.emvextension.protocol.StateMachine.State.SEND_HELLO;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StateMachineUtils {
    private static Map<StateMachine.State, String> stateNames;
    private static Map<String, StateMachine.State> namesState;

    static {
        Map<StateMachine.State, String> stateNamesTmp = new HashMap<StateMachine.State, String>();
        stateNamesTmp.put(INIT, "INIT");
        stateNamesTmp.put(SELECT, "SELECT");
        stateNamesTmp.put(RECEIVE_HELLO, "RECEIVE_HELLO");
        stateNamesTmp.put(SEND_HELLO, "SEND_HELLO");
        stateNamesTmp.put(RANGE, "RANGE");
        stateNamesTmp.put(AUTH, "AUTH");
        stateNamesTmp.put(CERT, "CERT");
        stateNamesTmp.put(FINISH, "FINISH");
        stateNames = Collections.unmodifiableMap(stateNamesTmp);

        Map<String, StateMachine.State> nameToStateTmp = new HashMap<>();
        nameToStateTmp.put("INIT", INIT);
        nameToStateTmp.put("SELECT", SELECT);
        nameToStateTmp.put("RECEIVE_HELLO", RECEIVE_HELLO);
        nameToStateTmp.put("SEND_HELLO", SEND_HELLO);
        nameToStateTmp.put("RANGE", RANGE);
        nameToStateTmp.put("AUTH", AUTH);
        nameToStateTmp.put("CERT", CERT);
        nameToStateTmp.put("FINISH", FINISH);
        namesState = Collections.unmodifiableMap(nameToStateTmp);
    }

    public static String stateToString(StateMachine.State state) {
        return stateNames.get(state);
    }

    public static StateMachine.State stringToState(String state) {
        return namesState.get(state);
    }

    public static List<String> getStates() {
        return stateNames.keySet().stream().sorted().map(StateMachineUtils::stateToString).collect(Collectors.toList());
    }
}
