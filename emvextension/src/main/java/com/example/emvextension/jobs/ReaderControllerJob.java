package com.example.emvextension.jobs;

import android.content.Context;

import com.example.emvextension.Apdu.ApduWrapperReader;
import com.example.emvextension.channel.Channel;
import com.example.emvextension.channel.UartChannelMock;
import com.example.emvextension.controller.ReaderController;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.utils.Timer;

import java.util.concurrent.Semaphore;

public class ReaderControllerJob extends Thread{

    private final ReaderController controller;

    public ReaderControllerJob(ReaderController controller) {
        this.controller = controller;
    }

    public void run() {
        controller.start();
    }
}
