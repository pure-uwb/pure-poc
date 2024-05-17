package ch.ethz.pure;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.settings, new SettingsFragment()).commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Respond to the action bar's Up/Home button
        if (item.getItemId() == android.R.id.home) {
            super.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            ListPreference emulatorPref;

            emulatorPref = findPreference("emulator");
            if (emulatorPref != null)
                emulatorPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    refreshDependencies("emulator", newValue);
                    return true;
                });

            refreshDependencies(null, null);
        }

        private void refreshDependencies(String key, Object newValue) {
            String emulator = null;

            ListPreference emulatorPref = findPreference("emulator");
            if (emulatorPref != null) {
                emulatorPref.setEnabled(true);

                if (emulatorPref.isEnabled()) {
                    emulator = emulatorPref.getValue();
                    if ("emulator".equals(key) && newValue != null) emulator = (String) newValue;
                } else emulatorPref.setValue("pos");
            }

            Preference pref;

            //card
            if ((pref = findPreference("ip")) != null) pref.setVisible("card".equals(emulator));

            //pos
            if ((pref = findPreference("save")) != null) pref.setVisible("pos".equals(emulator));
        }
    }
}