package ch.ethz.nfcrelay;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.emvextension.protocol.ProtocolModifier;
import ch.ethz.nfcrelay.nfc.ProtocolModifierImpl;

import ch.ethz.nfcrelay.nfc.Util;
import ch.ethz.nfcrelay.nfc.card.hce.CommandDispatcherImpl;
import ch.ethz.nfcrelay.nfc.card.hce.EMVraceApduService;

public class CardActivity extends AppCompatActivity {

    private ImageView ivOK;
    private ProgressBar pbTransacting;
    private TextView tvMsg;
    private boolean transacting, receivedGPO;
    private boolean isMock = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);

        EMVraceApduService.cardActivity = this;
        ProtocolModifier protocolModifier = Provider.getModifier(this,false);
        CommandDispatcherImpl dispatcher =  new CommandDispatcherImpl(protocolModifier);
        EMVraceApduService.dispatcher = dispatcher;
        protocolModifier.setNfcChannel(dispatcher);
        ivOK = findViewById(R.id.ivOK);
        pbTransacting = findViewById(R.id.pbTransacting);
        tvMsg = findViewById(R.id.tvMsg);
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
        Log.i(this.getClass().getName(), "ApduServiceDeactivated");
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