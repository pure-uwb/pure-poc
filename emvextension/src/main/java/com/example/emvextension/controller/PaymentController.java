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

    public PaymentController(Channel paymentChannel, Channel boardChannel, ProtocolExecutor protocol,
        Semaphore s, ApplicationCryptogram AC){
        this.emvChannel = paymentChannel;
        this.boardChannel = boardChannel;
        this.protocol = protocol;
        this.s = s;
        this.AC = AC;
        paymentSession = new Session(new CardStateMachine());
    }
    public void registerSessionListener(PropertyChangeListener listener){
        paymentSession.addPropertyChangeListener(listener);
    }
}
