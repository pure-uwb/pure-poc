package ch.ethz.pure;

import static android.nfc.NfcAdapter.FLAG_READER_NFC_A;
import static android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
import static ch.ethz.emvextension.controller.ReaderController.LOG_EVT;
import static ch.ethz.pure.nfc.BuildSettings.mockUart;
import static ch.ethz.pure.nfc.BuildSettings.number_of_tests;
import static ch.ethz.pure.nfc.BuildSettings.prerecordedBackend;
import static ch.ethz.pure.nfc.BuildSettings.transparentRelay;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceManager;

import com.example.emvextension.BuildConfig;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import ch.ethz.emvextension.protocol.ProtocolModifier;
import ch.ethz.pure.backend.CardBackend;
import ch.ethz.pure.backend.EmvTrace;
import ch.ethz.pure.backend.ReaderBackend;
import ch.ethz.pure.nfc.ProtocolModifierImpl;
import ch.ethz.pure.nfc.card.hce.EMVraceApduService;
import ch.ethz.pure.nfc.pos.NfcChannel;
import ch.ethz.pure.nfc.pos.PosEmulator;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback, PropertyChangeListener {

    private static final String[][] NFC_TECH_FILTER = new String[][]{new String[]{IsoDep.class.getName(), NfcA.class.getName(), NfcB.class.getName()}};
    private static final IntentFilter[] INTENT_FILTERS = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED), new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
    private static final int PORT = 8080;
    private static final int PORT_READER_TO_BACKEND = 8080;

    private LinearLayout layoutStatus;
    private TextView tvStatus, tvIP, tvLog;
    private FloatingActionButton fabCard;
    private FloatingActionButton fabSave;

    private CheckBoxPreference swUart;
    private CheckBoxPreference swBackend;
    private CheckBoxPreference swTransparent;
    private ServerSocket serverSocket;
    private String ip;

    private NfcAdapter nfcAdapter;
    private PendingIntent nfcIntent;
    private IsoDep tagComm;
    private boolean isPOS;
    private Semaphore readerSemaphore;

    private ActivityResultLauncher<Intent> launcher;
    private CharSequence logBackup = "";
    private FloatingActionButton fabPay;
    private List<Thread> threadList = new LinkedList<>();
    private ImageView successIcon;
    private TextView distanceTxt;
    private boolean cardStarted = false;

    private final String TAG = MainActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutStatus = findViewById(R.id.layoutStatus);
        tvStatus = findViewById(R.id.tvStatus);
        tvIP = findViewById(R.id.tvIP);
        tvLog = findViewById(R.id.tvLog);
        successIcon = findViewById(R.id.success_icon);
        distanceTxt = findViewById(R.id.distance);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        fabCard = findViewById(R.id.fabCard);
        fabSave = findViewById(R.id.fabSave);
        fabPay = findViewById(R.id.fabEuro);
        fabCard.setOnClickListener(view -> tryToStartCardEmulator());
        fabSave.setOnClickListener(view -> saveToStorage());
        readerSemaphore = new Semaphore(0);
        fabPay.setOnClickListener(view -> {
            readerSemaphore.release();
        });
        applySettings();
        if (!mockUart) {
            initializeUart();
        }
        if (!isPOS) tryToStartCardEmulator();
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> applySettings());
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
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0,
                    new Intent(INTENT_ACTION_GRANT_USB), flags);
            manager.requestPermission(driver.getDevice(), usbPermissionIntent);
        }
    }


    private String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            try {
                return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(ipInt).array()).getHostAddress();
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
        mockUart = prefs.getBoolean("mock_uart", true);
        prerecordedBackend = prefs.getBoolean("prerec_backend", false);
        transparentRelay = prefs.getBoolean("transparent_relay", false);
        Log.i(TAG, " Settings:" + "\nmockUart:\t" + mockUart +
                                       "\nprerecordedBackend:\t" + prerecordedBackend  +
                                       "\ntransparentRelay:\t" + transparentRelay);
        //refresh GUI accordingly
        if (isPOS) {
            setTitle(R.string.pos_emulator);
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            nfcAdapter.enableReaderMode(this, this,
                    FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK, null);
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
            fabPay.hide();
            if (nfcAdapter != null) {
                nfcAdapter.disableReaderMode(this);
            }
        }
    }

    @Override
    public void onResume() {
        //Enable reader mode when user comes back to the activity
        super.onResume();

        if (isPOS) {
            try {
                Log.i(TAG, "Start Socket on " + PORT_READER_TO_BACKEND);
                serverSocket = new ServerSocket(PORT_READER_TO_BACKEND);
//                serverSocket.setSoTimeout(0);
            } catch (IOException e) {
                showErrorOrWarning(e, false);
            }

            nfcAdapter.enableReaderMode(this, this,
                    FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK, null);
            if (tagComm != null && tagComm.isConnected())
                updateStatus(getString(R.string.card_connected), true);
            else updateStatus(getString(R.string.waiting_for_card), false);

        }
    }

    @Override
    public void onPause() {
        //Disable reader mode when user leaves the activity
        super.onPause();
        if (isPOS) {
            if (nfcAdapter != null) {
                nfcAdapter.disableReaderMode(this);
            }
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void tryToStartCardEmulator() {
        try {
            if (!cardStarted) {
                cardStarted = true;
                if (prerecordedBackend) {
                    EmvTrace emvTrace = new EmvTrace(this.getResources()
                            .openRawResource(R.raw.mastercard_transaction_prerec));
                    Thread cardBackend = CardBackend.getInstance(PORT, emvTrace);
                    if (!cardBackend.isAlive()) {
                        cardBackend.start();
                    }
                    Log.i(TAG, "Started mock card backend");
                    ip = getLocalIpAddress();
                }
            }
            CardEmulation cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
            ComponentName cmpName = new ComponentName(this, EMVraceApduService.class);

            //check that our APDU service is active
            if (!cardEmulation.isDefaultServiceForCategory(cmpName, CardEmulation.CATEGORY_PAYMENT)) {
                Snackbar.make(findViewById(R.id.layout_main), R.string.service_not_active, Snackbar.LENGTH_SHORT)
                        .setAction(R.string.enable, view -> startActivity(new Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS)))
                        .show();
            } else {
                Log.i(TAG, "Start card emulator");
                startCardEmulator();
            }
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
        runOnUiThread(() -> Toast.makeText(this, msg,
                lengthShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
    }

    public void showErrorOrWarning(final String msg, final boolean lengthShort) {
        runOnUiThread(() -> Snackbar.make(findViewById(R.id.layout_main), msg,
                lengthShort ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG).show());
    }

    public void showErrorOrWarning(@NonNull final Exception e, final boolean lengthShort) {
        e.printStackTrace();
        showErrorOrWarning(e.getMessage(), lengthShort);
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        Thread t;
        if (isPOS) {
            tagComm = IsoDep.get(tag);
            tagComm.setTimeout(30000);
            try {
                if (tagComm != null && !tagComm.isConnected()) tagComm.connect();

            } catch (IOException e) {
                this.showErrorOrWarning(e, true);
            }
            // NOTE: set number_of_tests to 1 to have a single transaction on every new discovered tag
            for (int i = 0; i < number_of_tests; i++) {
                runOnUiThread(() -> {
                    tvLog.setText("");
                    distanceTxt.setText("");
                    successIcon.setVisibility(View.INVISIBLE);

                });
                try {
                    serverSocket.close();
                    serverSocket = new ServerSocket(PORT_READER_TO_BACKEND);
                    Log.i(TAG, "Start server socket on " + PORT_READER_TO_BACKEND);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Log.i(TAG, "New Tag");

                updateStatus(getString(R.string.card_connected), true);
                ProtocolModifier modifier = Provider.getModifier(this, true);
                modifier.setNfcChannel(new NfcChannel(tagComm));
                List<Thread> newThreadList = new LinkedList<>();
                for (Thread thread : threadList) {
                    Log.i(TAG, "Interrupt thread:" + thread);
                    thread.interrupt();
                }
                if (prerecordedBackend) {
                    EmvTrace emvTrace = new EmvTrace(this.getResources().openRawResource(R.raw.mastercard_transaction_prerec));
                    Log.i(TAG, "Reader Backend create");
                    t = new ReaderBackend(getLocalIpAddress(), PORT_READER_TO_BACKEND, emvTrace, readerSemaphore);
                    t.start();
                    newThreadList.add(t);
                }
                t = new PosEmulator(this, tagComm, modifier, readerSemaphore);
                newThreadList.add(t);
                t.start();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Thread interrupted");
                }
                threadList = newThreadList;
            }
        }
    }

    public void showSuccess(boolean success) {
        runOnUiThread(() -> {
            if (success) {
                successIcon.setImageResource(R.drawable.checked);
            } else {
                successIcon.setImageResource(R.drawable.cancel);
            }
            successIcon.setVisibility(View.VISIBLE);
        });
    }

    public void showDistance(String distance) {
        runOnUiThread(() -> {
            distanceTxt.setText(distance);
        });

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ((evt.getPropertyName().equals("success"))) {
            this.showSuccess((boolean) evt.getNewValue());
        }

        if (evt.getPropertyName().equals("distance")) {
            this.showDistance((String) evt.getNewValue());
        }

        if (evt.getPropertyName().equals(LOG_EVT)) {
            this.appendToLog((String) evt.getOldValue());
            this.appendToLog((String) evt.getNewValue());

        }
    }
}