package ch.ethz.pure.backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.ethz.pure.nfc.Util;

public class EmvTrace {

    private Iterator<byte[]> commandsIterator;
    private Iterator<byte[]> responsesIterator;
    private final List<byte[]> responses = new ArrayList<>();
    List<byte[]> commands = new ArrayList<>();

    public EmvTrace(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split("]");
                if (tokens.length != 2) {
                    break;
                }
                String TAG = this.getClass().toString();

                if (tokens[0].contains("C-APDU")) {
                    commands.add(Util.hexToBytes(tokens[1].strip()));
                } else {
                    responses.add(Util.hexToBytes(tokens[1].strip()));
                }
            }
            reader.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        commandsIterator = commands.iterator();
        responsesIterator = responses.iterator();
    }

    public boolean commandsHasNext() {
        return commandsIterator.hasNext();
    }

    public boolean responsesHasNext() {
        return responsesIterator.hasNext();
    }

    public byte[] getCommand() {
        return commandsIterator.next();
    }

    public byte[] getResponse() {
        return responsesIterator.next();
    }

    public void resetResponses() {
        responsesIterator = responses.iterator();
    }

    public void resetCommands() {
        commandsIterator = commands.iterator();
    }
}
