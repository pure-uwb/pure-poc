package com.example.emvextension.utils;

import static com.example.emvextension.protocol.StateMachineUtils.stateToString;
import static com.example.emvextension.protocol.StateMachineUtils.stringToState;

import android.util.Log;

import com.example.emvextension.protocol.StateMachine;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

public class Timer implements PropertyChangeListener {
    Map<String, Long> timings = new HashMap<>();
    StateMachine stateMachine;

    private static final String TAG = Timer.class.toString();
    public Timer(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Log.i(TAG, "evt: " + evt.getPropertyName());
        if(evt.getPropertyName().equals("state")){
            timings.put((String)evt.getNewValue(), System.nanoTime());
            return;
        }
        if(evt.getPropertyName().equals("state_finish")){
            timings.put((String)evt.getOldValue(), System.nanoTime());
            this.report();
        }
    }

    public void report() {
        Map <String, Float> elapsedTime = new HashMap<>();
        for (String s: timings.keySet()){
            if(s.equals("FINISH")){
                continue;
            }
            String next = stateToString(stateMachine.next(stringToState(s)));
            int NANO_TO_MILLI = 1000000;
            if (next == null){
                break;
            }
            elapsedTime.put(String.format("%s->%s", s, next), ((float)(timings.get(next) - timings.get(s)))/NANO_TO_MILLI );
        }
        Log.i("Timing report", elapsedTime.toString());
    }
}
