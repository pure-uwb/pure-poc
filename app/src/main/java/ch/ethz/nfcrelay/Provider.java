package ch.ethz.nfcrelay;

import static ch.ethz.nfcrelay.nfc.BuildSettings.mockUart;
import static ch.ethz.nfcrelay.nfc.BuildSettings.transparentRelay;

import android.app.Activity;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.channel.UartChannel;
import com.example.emvextension.channel.UartChannelMock;
import com.example.emvextension.protocol.ProtocolModifier;

import ch.ethz.nfcrelay.nfc.ProtocolModifierImpl;
import ch.ethz.nfcrelay.nfc.TransparentProtocolModifier;

public class Provider {
    public static ProtocolModifier getModifier(Activity activity, boolean isReader){
        if(transparentRelay)
            return new TransparentProtocolModifier();
        return new ProtocolModifierImpl(activity, isReader);
    }

    public static Channel getUartChannel(Activity activity){
        if(mockUart)
            return new UartChannelMock();
        return UartChannel.getChannel(activity);
    }

}
