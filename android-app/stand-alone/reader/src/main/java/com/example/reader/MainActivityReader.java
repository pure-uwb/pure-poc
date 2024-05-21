package com.example.reader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.reader.ui.main.MainFragment;

public class MainActivityReader extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Bundle args = new Bundle();
        args.putString("role", "Reader");
        if (savedInstanceState == null) {
            Fragment f = MainFragment.newInstance();
            f.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, f)
                    .commitNow();
        }
    }
}