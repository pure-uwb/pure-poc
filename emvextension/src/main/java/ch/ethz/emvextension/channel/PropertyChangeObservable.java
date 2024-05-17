package ch.ethz.emvextension.channel;

import java.beans.PropertyChangeListener;

public interface PropertyChangeObservable {
    void addPropertyChangeListener(PropertyChangeListener listener);

    void notifyAllListeners(String desc, byte[] oldValue, byte[] newValue);
}
