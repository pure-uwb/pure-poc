package com.example.emvextension.controller;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeListener;

public abstract class PaymentController {
    protected Channel emvChannel;
    protected Channel boardChannel;
    protected Session paymentSession;

    protected ProtocolExecutor protocol;

    public PaymentController(Channel paymentChannel, Channel boardChannel, ProtocolExecutor protocol) {
        this.emvChannel = paymentChannel;
        this.boardChannel = boardChannel;
        this.protocol = protocol;
        paymentSession = new Session(new CardStateMachine());
    }

    public void registerSessionListener(PropertyChangeListener listener) {
        paymentSession.addPropertyChangeListener(listener);
    }
}
