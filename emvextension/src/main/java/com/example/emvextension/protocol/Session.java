package com.example.emvextension.protocol;

import android.util.Log;

import com.example.emvextension.channel.PropertyChangeObservable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;

public class Session  implements PropertyChangeObservable {

    private static final float MAX_DISTANCE = 0.50F;
    private KeyPair localKey;
    private PublicKey remoteKey;
    private byte [] secret;
    private final StateMachine stateMachine;
    Long pollRx;
    Long pollTx;
    Long respRx;
    Long respTx;
    Long finalTx;

    private float distance;
    private byte[] tagKey;
    private boolean signVerif;
    private byte[] AC;
    private RSAPublicKey secondaryKey;

    public Session(StateMachine stateMachine) {

        this.stateMachine = stateMachine;
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        ECGenParameterSpec ecsp;
        ecsp = new ECGenParameterSpec("secp256r1");
        try {
            kpg.initialize(ecsp);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
        localKey = kpg.genKeyPair();
        signVerif = false;
    }

    private static final String TAG = "Session";

    public KeyPair getLocalKey() {
        return localKey;
    }

    public void setLocalKey(KeyPair localKey) {
        this.localKey = localKey;
    }

    public PublicKey getRemoteKey() {
        return remoteKey;
    }

    public void setRemoteKey(PublicKey remoteKey) {
        this.remoteKey = remoteKey;
    }

    public byte[] getSecret() {
        return secret;
    }

    public void setSecret(byte[] secret) {
        this.secret = secret;
    }
    public StateMachine.State getState(){
        return stateMachine.getState();
    }

    public void step(){
        String oldState = stateMachine.getStateString();
        Log.i("STATE_MACHINE", "From:" + oldState);
        stateMachine.step();
        Log.i("STATE_MACHINE", "To:" + stateMachine.getStateString());
        notifyAllListeners("state", oldState, stateMachine.getStateString());
       }

    public void setTimings(Long pollRx, Long pollTx, Long respRx, Long respTx, Long finalTx) {
        this.pollRx = pollRx;
        this.pollTx = pollTx;
        this.respRx = respRx;
        this.respTx = respTx;
        this.finalTx = finalTx;
        Log.i("Session", pollRx.toString());
        Log.i("Session", pollTx.toString());
        Log.i("Session", respRx.toString());
        Log.i("Session", respTx.toString());
        Log.i("Session", finalTx.toString());
    }

    public void setDistance(Float distance) {
        this.distance = distance;
        notifyAllListeners("distance", null, "Distance: " + distance );
    }

    public float getDistance() {
        return distance;
    }

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void notifyAllListeners(String desc, byte[] oldValue, byte[] newValue) {
        support.firePropertyChange(desc, oldValue, newValue);
    }

    public void notifyAllListeners(String desc, Object oldValue, Object newValue) {
        support.firePropertyChange(desc, oldValue, newValue);
    }

    public PropertyChangeListener[] getListeners(){
        return support.getPropertyChangeListeners();
    }

    public void finish(){
        //Necessary to register the time it took to finish

        notifyAllListeners("state_finish", stateMachine.getStateString(),"EXIT");
        notifyAllListeners("success", !this.isSuccess(), this.isSuccess());
    }

    public byte[] getTranscript(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );

        try {
            outputStream.write(this.localKey.getPublic().getEncoded());
            outputStream.write(this.remoteKey.getEncoded());
            if( respTx != null & pollRx != null){
                outputStream.write(longToBytes(pollTx));
                outputStream.write(longToBytes(respRx));
                outputStream.write(longToBytes(finalTx));
                outputStream.write(AC);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

    public byte[] getRemoteTranscript(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );

        try {
            outputStream.write(this.remoteKey.getEncoded());
            outputStream.write(this.localKey.getPublic().getEncoded());
            if( respTx != null & pollRx != null & stateMachine.getState() == StateMachine.State.AUTH){
                //TODO: modify state once new states are added
                outputStream.write(longToBytes(pollTx));
                outputStream.write(longToBytes(respRx));
                outputStream.write(longToBytes(finalTx));
                outputStream.write(AC);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }
    public byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public void setTagKey(byte[] tagKey) {
        this.tagKey = tagKey;
    }

    public byte[] getTagKey(){return tagKey;}

    public void setSignVerif(boolean b) {
        this.signVerif = b;
    }

    public boolean isSuccess(){
        return this.signVerif & this.distance < MAX_DISTANCE;
    }

    public void setAC(byte[] ac) {
        this.AC = ac;
    }
    
    public void setSecondaryKey(RSAPublicKey key){
        this.secondaryKey = key;
    }
    public RSAPublicKey getSecondaryKey(){
        return secondaryKey;
    }
}
