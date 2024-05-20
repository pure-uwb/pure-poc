package com.example.card.ui.main;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.card.R;
import com.example.emvextension.Apdu.ApduWrapperCard;
import com.example.emvextension.channel.CardNfcChannel;
import com.example.emvextension.channel.UartChannel;
import com.example.emvextension.controller.CardController;
import com.example.emvextension.controller.PaymentController;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.utils.Timer;

import org.jetbrains.annotations.NotNull;


public class MainFragment extends Fragment {

    private MainViewModel mViewModel;
    private String role;
    private PaymentController controller;

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        role = getArguments().getString("role");
        if (role == null) {
            Log.i("Frag", "Role is null");
        }

        controller = new CardController(new CardNfcChannel(requireActivity()), new UartChannel(this.requireActivity()), new ProtocolExecutor(new ApduWrapperCard(), getContext()));
//        controller = new CardController(new CardNfcChannel(requireActivity()), new UartChannelMock(), new ProtocolExecutor(new ApduWrapperCard(), getContext()));

        controller.registerSessionListener(new Timer(new CardStateMachine(), this.requireActivity()));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        TextView rec = view.findViewById(R.id.receive_text);
        TextView res = view.findViewById(R.id.result);

        controller.registerSessionListener((evt) -> {
            requireActivity().runOnUiThread(() -> {
                if (evt.getPropertyName().equals("state")) {
                    if (evt.getOldValue().equals("INIT")) {
                        res.setText("");
                    }
                    res.append(String.format("%s -> %s\n", evt.getOldValue(), evt.getNewValue()));
                }
            });
        });
        return view;
    }

    @Override
    public void onViewCreated(@NonNull @NotNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView title = view.findViewById(R.id.frame_title);
        Log.i("Fragm", "Role " + role);
        title.setText(role);
    }
}