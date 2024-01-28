package ch.ethz.pure;

import static ch.ethz.pure.nfc.BuildSettings.mockUart;
import static ch.ethz.pure.nfc.BuildSettings.transparentRelay;

import android.app.Activity;

import ch.ethz.emvextension.channel.Channel;
import ch.ethz.emvextension.channel.UartChannel;
import ch.ethz.emvextension.channel.UartChannelMock;
import ch.ethz.emvextension.protocol.ProtocolModifier;

import ch.ethz.pure.nfc.ProtocolModifierImpl;
import ch.ethz.pure.nfc.TransparentProtocolModifier;

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
