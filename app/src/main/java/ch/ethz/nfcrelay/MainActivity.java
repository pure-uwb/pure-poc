package ch.ethz.nfcrelay;

import static ch.ethz.nfcrelay.mock.Constants.isMock;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.example.emvextension.protocol.ProtocolModifier;
import ch.ethz.nfcrelay.nfc.ProtocolModifierImpl;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ch.ethz.nfcrelay.mock.CardBackend;
import ch.ethz.nfcrelay.mock.EmvTrace;
import ch.ethz.nfcrelay.mock.ReaderBackend;
import ch.ethz.nfcrelay.nfc.Util;
import ch.ethz.nfcrelay.nfc.card.ResponseResolver;
import ch.ethz.nfcrelay.nfc.card.hce.EMVraceApduService;
import ch.ethz.nfcrelay.nfc.pos.NfcChannel;
import ch.ethz.nfcrelay.nfc.pos.RelayPosEmulator;

public class MainActivity extends AppCompatActivity {

    private static final String[][] NFC_TECH_FILTER = new String[][]{new String[]{IsoDep.class.getName(), NfcA.class.getName(), NfcB.class.getName()}};
    private static final IntentFilter[] INTENT_FILTERS = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED), new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
    private static final int PORT = 8080;

    private LinearLayout layoutStatus;
    private TextView tvStatus, tvIP, tvLog;
    private FloatingActionButton fabCard;
    private FloatingActionButton fabSave;

    private ServerSocket serverSocket;
    private String ip;

    private NfcAdapter nfcAdapter;
    private PendingIntent nfcIntent;
    private IsoDep tagComm;
    private boolean isPOS;

    private ActivityResultLauncher<Intent> launcher;
    private CharSequence logBackup = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutStatus = findViewById(R.id.layoutStatus);
        tvStatus = findViewById(R.id.tvStatus);
        tvIP = findViewById(R.id.tvIP);
        tvLog = findViewById(R.id.tvLog);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        fabCard = findViewById(R.id.fabCard);
        fabSave = findViewById(R.id.fabSave);

        fabCard.setOnClickListener(view -> tryToStartCardEmulator());
        fabSave.setOnClickListener(view -> saveToStorage());

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

        applySettings();
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> applySettings());
    }

    private String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            try {
                return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
            } catch (UnknownHostException e) {
                showErrorOrWarning(e, true);
            }
        }
        return "0.0.0.0";
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            if (isPOS) logBackup = tvLog.getText();
            launcher.launch(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void applySettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isPOS = "pos".equals(prefs.getString("emulator", "pos"));//default emulator is POS
        ip = prefs.getString("ip", "0.0.0.0");

        //refresh GUI accordingly
        if (isPOS) {
            setTitle(R.string.pos_emulator);

            //layoutStatus.setVisibility(View.VISIBLE);
            tvIP.setText(getString(R.string.ip, getLocalIpAddress()));
            tvIP.setVisibility(View.VISIBLE);
            tvLog.setVisibility(View.VISIBLE);
            tvLog.setText(logBackup);

            fabCard.hide();
            fabSave.show();

        } else {
            setTitle(R.string.card_emulator);

            tvStatus.setText(R.string.press_pay);
            tvIP.setVisibility(View.GONE);
            layoutStatus.setBackgroundResource(android.R.color.holo_green_dark);
            tvLog.setVisibility(View.GONE);

            fabCard.show();
            fabSave.hide();
        }
    }

    @Override
    public void onResume() {
        //Enable reader mode when user comes back to the activity
        super.onResume();

        if (isPOS) {
            try {
                if (nfcAdapter != null)
                    nfcAdapter.enableForegroundDispatch(this, nfcIntent, INTENT_FILTERS, NFC_TECH_FILTER);
            } catch (Exception e) {
                showErrorOrWarning(e, true);
            }

            if (tagComm != null && tagComm.isConnected())
                updateStatus(getString(R.string.card_connected), true);
            else updateStatus(getString(R.string.waiting_for_card), false);
        }
    }

    @Override
    public void onPause() {
        //Disable reader mode when user leaves the activity
        super.onPause();
        if (isPOS && nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //A card is detected
        super.onNewIntent(intent);

        if (isPOS) {
            Log.i("MainActivity", "NEW INTENT");
            tagComm = IsoDep.get(intent.getParcelableExtra(NfcAdapter.EXTRA_TAG));
            updateStatus(getString(R.string.card_connected), true);

            try {
                serverSocket = new ServerSocket(PORT);
            } catch (IOException e) {
                showErrorOrWarning(e, false);
            }
            ProtocolModifier modifier = new ProtocolModifierImpl(this,true);
            modifier.setNfcChannel(new NfcChannel(tagComm));
            new RelayPosEmulator(this, tagComm,  modifier).start();
            if (isMock) {
                EmvTrace emvTrace = new EmvTrace(this.getResources().openRawResource(R.raw.mastercad_to_selecta_2chf));
                new ReaderBackend(ip, PORT, emvTrace).start();
            }
        }
    }

    public void tryToStartCardEmulator() {
        try {
            if (isMock) {
                EmvTrace emvTrace = new EmvTrace(this.getResources().openRawResource(R.raw.mastercad_to_selecta_2chf));
                Thread cardBackend = CardBackend.getInstance( PORT, emvTrace);
                cardBackend.start();
                Log.i("MainActivity", "Started mock card backend");
            }
            CardEmulation cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
            ComponentName cmpName = new ComponentName(this, EMVraceApduService.class);
            //check that our APDU service is active
            if (!cardEmulation.isDefaultServiceForCategory(cmpName, CardEmulation.CATEGORY_PAYMENT)) {
                Snackbar.make(findViewById(R.id.layout_main), R.string.service_not_active, Snackbar.LENGTH_SHORT).setAction(R.string.enable, view -> startActivity(new Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS)))

                        .show();
            } else {
                //test connection with the remote POS emulator
                //if successful, such thread will launch the card activity
                new ResponseResolver(null, ip, PORT, Util.PPSE_APDU_SELECT, true, this, null).start();
            }
            //new ResponseResolver(null, ip, PORT,
            //        Util.PPSE_APDU_SELECT, true, this).start();
        } catch (Exception e) {
            showErrorOrWarning(e, true);
        }
    }

    public void startCardEmulator() {
        EMVraceApduService.ip = ip;
        EMVraceApduService.port = PORT;
        runOnUiThread(() -> startActivity(new Intent(this, CardActivity.class)));
    }

    public String getLogString() {
        return tvLog.getText().toString();
    }

    public void saveToStorage() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String filename = "log_" + System.currentTimeMillis() + ".txt";
        String saveMode = prefs.getString("save", "");

        if ("storage".equals(saveMode) || "clipboard_n_storage".equals(saveMode)) {
            new Thread(() -> {
                try {
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);

                    FileOutputStream out = new FileOutputStream(file);
                    out.write(getLogString().getBytes());
                    out.close();

                    showSuccess(getString(R.string.saved_in, file.getPath()), true);

                } catch (IOException e) {
                    //likely running Android 10+ or permission to storage not granted
                    showErrorOrWarning(e, true);
                }
            }).start();
        }

        if ("clipboard".equals(saveMode) || "clipboard_n_storage".equals(saveMode)) {
            try {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null) {
                    cb.setPrimaryClip(ClipData.newPlainText(filename, getLogString()));
                    showSuccess(getString(R.string.copied_to_clip), true);
                }

            } catch (Exception e) {
                showErrorOrWarning(e, true);
            }
        }
    }

    public void appendToLog(final String msg) {
        runOnUiThread(() -> tvLog.append(msg + "\n"));
    }

    public void updateStatus(final String msg, final boolean cardConnected) {
        runOnUiThread(() -> {
            layoutStatus.setBackgroundResource(cardConnected ? android.R.color.holo_green_dark : android.R.color.holo_red_dark);
            tvStatus.setText(msg);
        });
    }

    public void showSuccess(final String msg, final boolean lengthShort) {
        runOnUiThread(() -> Toast.makeText(this, msg, lengthShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
    }

    public void showErrorOrWarning(final String msg, final boolean lengthShort) {
        runOnUiThread(() -> Snackbar.make(findViewById(R.id.layout_main), msg, lengthShort ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG).show());
    }

    public void showErrorOrWarning(@NonNull final Exception e, final boolean lengthShort) {
        e.printStackTrace();
        showErrorOrWarning(e.getMessage(), lengthShort);
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
}