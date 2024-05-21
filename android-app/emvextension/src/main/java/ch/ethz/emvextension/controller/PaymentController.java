package ch.ethz.emvextension.controller;

import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

import ch.ethz.emvextension.channel.Channel;
import ch.ethz.emvextension.protocol.ApplicationCryptogram;
import ch.ethz.emvextension.protocol.ProtocolExecutor;
import ch.ethz.emvextension.protocol.Session;

public abstract class PaymentController {
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

    public PaymentController(Channel paymentChannel, Channel boardChannel, ProtocolExecutor protocol) {
        this.emvChannel = paymentChannel;
        this.boardChannel = boardChannel;
        this.protocol = protocol;

    }

    public void initialize(Semaphore parsingSemaphore, ApplicationCryptogram AC, Session session) {
        this.parsingSemaphore = parsingSemaphore;
        this.AC = AC;
        paymentSession = session;
    }

    public void registerSessionListener(PropertyChangeListener listener) {
        paymentSession.addPropertyChangeListener(listener);
    }
}
