package com.example.emvextension.controller;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

public abstract class PaymentController{
    protected Channel emvChannel;
    protected Channel boardChannel;
    protected Session paymentSession;

    protected ProtocolExecutor protocol;
    protected Semaphore parsingSemaphore;
    protected ApplicationCryptogram AC;

    public Semaphore getSemaphore() {
        return parsingSemaphore;
    }

    public ApplicationCryptogram getAC() {
        return AC;
    }

    public PaymentController(Channel paymentChannel, Channel boardChannel, ProtocolExecutor protocol){
        this.emvChannel = paymentChannel;
        this.boardChannel = boardChannel;
        this.protocol = protocol;

    }

    public void initialize(Semaphore parsingSemaphore, ApplicationCryptogram AC, Session session){
        this.parsingSemaphore = parsingSemaphore;
        this.AC = AC;
        paymentSession = session;
    }

    public void registerSessionListener(PropertyChangeListener listener){
        paymentSession.addPropertyChangeListener(listener);
    }
}
