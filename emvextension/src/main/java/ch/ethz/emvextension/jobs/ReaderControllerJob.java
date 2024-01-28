package ch.ethz.emvextension.jobs;

import ch.ethz.emvextension.controller.ReaderController;

public class ReaderControllerJob extends Thread{

    private final ReaderController controller;

    public ReaderControllerJob(ReaderController controller) {
        this.controller = controller;
    }

    public void run() {
        controller.start();
    }
}
