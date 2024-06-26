package ch.ethz.pure;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.emvextension.BuildConfig;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

import ch.ethz.emvextension.protocol.ProtocolModifier;
import ch.ethz.pure.nfc.BuildSettings;
import ch.ethz.pure.nfc.ProtocolModifierImpl;
import ch.ethz.pure.nfc.Util;
import ch.ethz.pure.nfc.card.hce.CommandDispatcherImpl;
import ch.ethz.pure.nfc.card.hce.EMVraceApduService;

public class CardActivity extends AppCompatActivity {

    private ImageView ivOK;
    private ProgressBar pbTransacting;
    private TextView tvMsg;
    private boolean transacting, receivedGPO;
    private final boolean isMock = true;
    private final String TAG = CardActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);

        EMVraceApduService.cardActivity = this;
        ProtocolModifier protocolModifier = Provider.getModifier(this, false);
        CommandDispatcherImpl dispatcher = new CommandDispatcherImpl(protocolModifier, BuildSettings.transparentRelay);
        EMVraceApduService.dispatcher = dispatcher;
        protocolModifier.setNfcChannel(dispatcher);
        ivOK = findViewById(R.id.ivOK);
        pbTransacting = findViewById(R.id.pbTransacting);
        tvMsg = findViewById(R.id.tvMsg);
        initializeUart();
    }

    private void initializeUart() {
        UsbManager manager = (UsbManager) this.getBaseContext().getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        if (!manager.hasPermission(driver.getDevice())) {
            Log.i(TAG, "Request permission");
            int flags = PendingIntent.FLAG_MUTABLE;
            String INTENT_ACTION_GRANT_USB = BuildConfig.LIBRARY_PACKAGE_NAME + ".GRANT_USB";
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            manager.requestPermission(driver.getDevice(), usbPermissionIntent);
        }
    }

    public void onApduCommandReceived(byte[] cmd) {
        if (!transacting) {
            transacting = true;
            runOnUiThread(() -> {
                if (tvMsg != null) tvMsg.setText(R.string.processing);
                ivOK.setVisibility(View.INVISIBLE);
                pbTransacting.setVisibility(View.VISIBLE);
            });
        }
        if (Util.isGPO(cmd))
            receivedGPO = true;
    }

    public void onApduServiceDeactivated(int reason) {
        Log.i(TAG, "ApduServiceDeactivated");
        if (receivedGPO && transacting && reason == 0) {
            receivedGPO = transacting = false;
            runOnUiThread(() -> {
                if (tvMsg != null) tvMsg.setText(R.string.completed);
                ivOK.setVisibility(View.VISIBLE);
                pbTransacting.setVisibility(View.INVISIBLE);
            });
        }
    }
}