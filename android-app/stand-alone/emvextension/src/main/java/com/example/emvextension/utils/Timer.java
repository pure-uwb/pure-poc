package com.example.emvextension.utils;

import static com.example.emvextension.protocol.StateMachineUtils.stateToString;
import static com.example.emvextension.protocol.StateMachineUtils.stringToState;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.example.emvextension.BuildSettings;
import com.example.emvextension.protocol.StateMachine;
import com.example.emvextension.protocol.StateMachineUtils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Timer implements PropertyChangeListener {
    Map<String, Long> timings = new HashMap<>();
    StateMachine stateMachine;
    Activity activity;
    float parallelRangingTime = -1;
    private static final String TAG = Timer.class.toString();

    public Timer(StateMachine stateMachine, Activity activity) {
        this.stateMachine = stateMachine;
        this.activity = activity;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Log.i(TAG, "evt: " + evt.getPropertyName());
        if (evt.getPropertyName().equals("state")) {
            Log.i("TIMER", "State: " + evt.getNewValue() + "\t Time:" + System.nanoTime());
            timings.put((String) evt.getNewValue(), System.nanoTime());
            return;
        }
        if (evt.getPropertyName().equals("RANGE")) {
            parallelRangingTime = (float) evt.getNewValue();
        }

        if (evt.getPropertyName().equals("state_finish")) {
            timings.put("FINISH", System.nanoTime());
            this.report();
        }
    }

    public void report() {
        Map<StateMachine.State, Float> elapsedTime = new TreeMap<>();
        for (String s : StateMachineUtils.getStates()) {
            if (s.equals("FINISH")) {
                continue;
            }
            try {
                String next = stateToString(stateMachine.next(stringToState(s)));
                int NANO_TO_MILLI = 1000000;
                elapsedTime.put(stateMachine.next(stringToState(s)), ((float) (timings.get(next) - timings.get(s))) / NANO_TO_MILLI);
                Log.i("Timer", next + ":\t" + elapsedTime.get(stateMachine.next(stringToState(s))));
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        float totalTime = ((float) (timings.get("FINISH") - timings.get("INIT"))) / 1000000;

        //Save to file
        if (BuildSettings.number_of_tests > 1) {
            Log.i("Timings", "SAVING FILE in " + activity.getFilesDir());
            if (!Arrays.asList(activity.fileList()).contains(BuildSettings.outputFileName)) {
                try (FileOutputStream fos = activity.openFileOutput(BuildSettings.outputFileName, Context.MODE_PRIVATE)) {
                    List<String> timingsList = StateMachineUtils.getStates()
                            .subList(1, StateMachineUtils.getStates().size());
                    timingsList.add("PARALLEL_RANGING");
                    timingsList.add("TOTAL");
                    fos.write(String.join(",", timingsList).getBytes());
                    fos.write("\n".getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try (FileOutputStream fos = activity.openFileOutput(BuildSettings.outputFileName,
                    Context.MODE_APPEND)) {
                List<String> timings = elapsedTime.values().stream()
                        .map((x -> {
                            return String.format("%.2f", x);
                        }))
                        .collect(Collectors.toList());
                timings.add(String.format("%.2f", parallelRangingTime));
                timings.add(String.format("%.2f", totalTime));
                fos.write(String.join(",", timings).getBytes());
                fos.write(",".getBytes());
                fos.write("\n".getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
