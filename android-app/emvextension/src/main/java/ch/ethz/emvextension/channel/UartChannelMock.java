package ch.ethz.emvextension.channel;

import static ch.ethz.emvextension.channel.UartChannel.READ_DATA;

import java.util.Random;

public class UartChannelMock extends Channel {
    Random rand = new Random();


    @Override
    public byte[] read() {
        return new byte[]{25, -85, 30, 112, -66, -25, 127, 91, 5, -92, -81, -76, 1, 114, 44, -71, '0', '0', '.', '2', '5'};
    }

    @Override
    public void write(byte[] payload) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int success = rand.nextInt(2);
        if (success == 1) {
            notifyAllListeners(READ_DATA, null, new byte[]{25, -85, 30, 112, -66, -25, 127, 91, 5, -92, -81, -76, 1, 114, 44, -71, 1, 114, 44, -71, '0', '0', '.', '2', '5'});
        } else {
            notifyAllListeners(READ_DATA, null, new byte[]{25, -85, 30, 112, -66, -25, 127, 91, 5, -92, -81, -76, 1, 114, 44, -71, 1, 114, 44, -71, '1', '0', '.', '0', '0'});
        }

    }
}
