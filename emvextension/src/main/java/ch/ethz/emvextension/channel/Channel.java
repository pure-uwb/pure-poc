package ch.ethz.emvextension.channel;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public abstract class Channel implements PropertyChangeObservable {
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private String value;
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void notifyAllListeners(String desc, byte[] oldValue, byte[] newValue){
        support.firePropertyChange(desc, oldValue, newValue);
    }
    public abstract byte[] read();
    public abstract void write(byte[] payload);




}
