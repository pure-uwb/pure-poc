package com.example.emvextension.channel;

import java.beans.PropertyChangeListener;

public interface PropertyChangeObservable {
    public void addPropertyChangeListener(PropertyChangeListener listener);

    public void notifyAllListeners(String desc, byte[] oldValue, byte[] newValue);
}
