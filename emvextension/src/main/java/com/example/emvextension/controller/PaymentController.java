package com.example.emvextension.controller;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

public abstract class PaymentController{
    protected Channel emvChannel;
    protected Channel boardChannel;
    protected Session paymentSession;

    protected ProtocolExecutor protocol;
    protected Semaphore s;
    protected ApplicationCryptogram AC;

    public Semaphore getSemaphore() {
        return s;
    }

    public ApplicationCryptogram getAC() {
        return AC;
    }

    public PaymentController(Channel paymentChannel, Channel boardChannel, ProtocolExecutor protocol){
        this.emvChannel = paymentChannel;
        this.boardChannel = boardChannel;
        this.protocol = protocol;

    }

    public void initialize(Semaphore s, ApplicationCryptogram AC, Session session){
        this.s = s;
        this.AC = AC;
        paymentSession = session;
    }

    public void registerSessionListener(PropertyChangeListener listener){
        paymentSession.addPropertyChangeListener(listener);
    }
}
