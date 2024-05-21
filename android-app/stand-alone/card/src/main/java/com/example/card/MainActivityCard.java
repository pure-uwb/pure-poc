package com.example.card;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.card.ui.main.MainFragment;
import com.example.emvextension.MyHostApduService;

public class MainActivityCard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = new Intent(this, MyHostApduService.class);
        startService(intent);

        Bundle args = new Bundle();
        args.putString("role", "Card");
        if (savedInstanceState == null) {
            Fragment f = MainFragment.newInstance();
            f.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, f)
                    .commitNow();
        }
    }
}