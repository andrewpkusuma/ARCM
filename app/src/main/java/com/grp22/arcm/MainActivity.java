package com.grp22.arcm;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private int delay;
    private boolean isArena;
    private ResponseReceiver receiver;
    private IntentFilter filter;
    private TextView status;
    private BluetoothConnectService mService;
    private boolean mBound = false;
    private boolean isRegistered = false;
    private ProgressDialog progressDialog;
    private boolean isPreviouslyRecovered;
    private final int REQ_CODE_SPEECH_INPUT = 69;
    private int orientation = 0; // 0 = up, 1 = right, 2 = down, 3 = left
    private final SpeechCommandProcessor processor = new SpeechCommandProcessor();
    private final Object lock = new Object();

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothConnectService.LocalBinder binder = (BluetoothConnectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        refreshSettings();

        status = (TextView) findViewById(R.id.status);

        Button stopButton = (Button) findViewById(R.id.stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Disconnecting...", Toast.LENGTH_SHORT).show();
                mService.stop();
                unbindService(mConnection);
                mBound = false;
            }
        });

        Button sendText = (Button) findViewById(R.id.send_predefined_text);
        sendText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SendTextDialogFragment dialogFragment = SendTextDialogFragment.newInstance();
                dialogFragment.show(getSupportFragmentManager(), "Send Text");
            }
        });

        final Button refreshMap = (Button) findViewById(R.id.refresh_map);

        ToggleButton toggleRefresh = (ToggleButton) findViewById(R.id.toggle_refresh);
        toggleRefresh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                refreshMap.setEnabled(!isChecked);
            }
        });

        ImageButton forward = (ImageButton) findViewById(R.id.forward);
        forward.setOnTouchListener(new ControllerListener("forward"));

        ImageButton reverse = (ImageButton) findViewById(R.id.reverse);
        reverse.setOnTouchListener(new ControllerListener("reverse"));

        ImageButton left = (ImageButton) findViewById(R.id.left);
        left.setOnTouchListener(new ControllerListener("left"));

        ImageButton right = (ImageButton) findViewById(R.id.right);
        right.setOnTouchListener(new ControllerListener("right"));

        ImageButton rotateLeft = (ImageButton) findViewById(R.id.rotate_left);
        rotateLeft.setOnTouchListener(new ControllerListener("rotateLeft"));

        ImageButton rotateRight = (ImageButton) findViewById(R.id.rotate_right);
        rotateRight.setOnTouchListener(new ControllerListener("rotateRight"));

        Button exploreArena = (Button) findViewById(R.id.explore_arena);
        exploreArena.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.sendToOutputStream("beginExploration");
            }
        });

        Button fastestPath = (Button) findViewById(R.id.show_fastest_path);
        fastestPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.sendToOutputStream("beginFastestPath");
            }
        });

        Button speechCommand = (Button) findViewById(R.id.speech_command);
        speechCommand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                promptSpeechInput();
            }
        });

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Connection Interrupted");
        progressDialog.setMessage("Attempting to reconnect. Please wait...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setIndeterminate(true);
        progressDialog.setCanceledOnTouchOutside(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                SettingsDialogFragment dialogFragment = SettingsDialogFragment.newInstance();
                dialogFragment.show(getSupportFragmentManager(), "Settings");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BluetoothConnectService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        filter = new IntentFilter();
        filter.addAction(BluetoothConnectService.CONNECTION_INTERRUPTED);
        filter.addAction(BluetoothConnectService.CONNECTION_RECOVERED);
        filter.addAction(BluetoothConnectService.DISCONNECTED);
        filter.addAction(BluetoothConnectService.STRING_RECEIVED);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new ResponseReceiver();
        registerReceiver(receiver, filter);
        isRegistered = true;
    }

    @Override
    protected void onDestroy() {
        mService.stop();
        if (isRegistered) {
            unregisterReceiver(receiver);
            Log.d("Unregistered", "yay");
            isRegistered = false;
        }
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        super.onDestroy();
    }

    private void sendMessage(String message) {
        mService.sendToOutputStream(message);
    }

    public void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Your wish is my command");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "Speech not supported",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    processCommand(result.get(0));
                }
                break;
            }
        }
    }

    public void sendCommand(String command) {
        String[] commandList = {"forward", "rotateLeft", "reverse", "rotateRight"};

        switch (command) {
            case "forward":
                mService.sendToOutputStream(commandList[(orientation + 4) % 4]);
                break;
            case "right":
                mService.sendToOutputStream(commandList[(orientation + 3) % 4]);
                break;
            case "reverse":
                mService.sendToOutputStream(commandList[(orientation + 2) % 4]);
                break;
            case "left":
                mService.sendToOutputStream(commandList[(orientation + 1) % 4]);
                break;
            case "rotateRight":
                mService.sendToOutputStream(commandList[3]);
                break;
            case "rotateLeft":
                mService.sendToOutputStream(commandList[1]);
                break;
        }
    }

    private void processCommand(String speech) {
        processor.process(speech);

        final String command = processor.getCommand();
        if (command != null) {
            if (processor.isRepeatable()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int initialOrientation = orientation;
                        int repetition = processor.getRepetition();
                        if (isArena)
                            repetition += Math.abs(orientation - processor.getTargetOrientation()) % 2;
                        else
                            repetition += processor.getTargetOrientation() % 2;
                        for (int i = 0; i < repetition; i++) {
                            synchronized (lock) {
                                if (!isArena)
                                    orientation -= initialOrientation;
                                sendCommand(command);
                            }
                            SystemClock.sleep(delay);
                        }
                    }
                }).start();
            } else {
                mService.sendToOutputStream(command);
            }
        }
    }

    private void handleStringInput(String input) {
        try {
            JSONObject inputJsonObject = new JSONObject(input);
            if (inputJsonObject.has("robotPosition")) {
                String positionString = inputJsonObject.getString("robotPosition");
                String[] positionStrings = positionString.substring(1, positionString.length() - 1).split(",");
                int[] positions = new int[positionStrings.length];
                for (int i = 0; i < positionStrings.length; i++)
                    positions[i] = Integer.parseInt(positionStrings[i]);
                synchronized (lock) {
                    orientation = positions[2] / 90;
                }
            } else if (inputJsonObject.has("grid")) {
                status.setText("MAP UPDATED");
            } else if (inputJsonObject.has("status")) {
                String statusText = inputJsonObject.getString("status");
                status.setText(statusText.toUpperCase());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void refreshSettings() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String movement = sharedPreferences.getString("movement", "robot");
        if (movement.equals("arena"))
            isArena = true;
        else if (movement.equals("robot"))
            isArena = false;
        Log.d("isArena", String.valueOf(isArena));
        delay = sharedPreferences.getInt("delay", 500);
        Log.d("delay", Integer.toString(delay));
        Log.d("timeout", Integer.toString(sharedPreferences.getInt("timeout", 10)));
        if (mBound)
            mService.setTimeout(sharedPreferences.getInt("timeout", 10));
    }

    public class ResponseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothConnectService.CONNECTION_INTERRUPTED:
                    isPreviouslyRecovered = false;
                    progressDialog.show();
                    break;
                case BluetoothConnectService.CONNECTION_RECOVERED:
                    if (!isPreviouslyRecovered) {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Connection recovered", Toast.LENGTH_SHORT).show();
                        isPreviouslyRecovered = true;
                    }
                    break;
                case BluetoothConnectService.DISCONNECTED:
                    unregisterReceiver(receiver);
                    Log.d("Unregistered", "yay");
                    isRegistered = false;
                    Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
                    Intent begin = new Intent(getApplicationContext(), BluetoothConnectActivity.class);
                    startActivity(begin);
                    break;
                case BluetoothConnectService.STRING_RECEIVED:
                    String message = intent.getStringExtra("message");
                    handleStringInput(message);
                    break;
            }
        }
    }

    private class ControllerListener implements View.OnTouchListener {
        String command;
        Thread sendCommandThread;

        public ControllerListener(String command) {
            this.command = command;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                final int initialOrientation = orientation;
                sendCommandThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            synchronized (lock) {
                                if (!isArena)
                                    orientation -= initialOrientation;
                                sendCommand(command);
                            }
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                });
                sendCommandThread.start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
                sendCommandThread.interrupt();
            return true;
        }
    }

    public static class SendTextDialogFragment extends DialogFragment implements View.OnClickListener {

        private SharedPreferences sharedPreferences;
        private Button f1Button;
        private EditText f1Input;
        private Button f2Button;
        private EditText f2Input;

        public SendTextDialogFragment() {
        }

        public static SendTextDialogFragment newInstance() {
            return new SendTextDialogFragment();
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // setup dialog: buttons, title etc
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity())
                    .setTitle("Send Text")
                    .setPositiveButton("Done",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    );

            LayoutInflater inflater = getActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.fragment_send_text_dialog, null);

            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

            f1Button = (Button) view.findViewById(R.id.f1_send);
            f1Input = (EditText) view.findViewById(R.id.f1_input);
            f1Input.setText(sharedPreferences.getString("f1", ""));
            f1Button.setOnClickListener(this);

            f2Button = (Button) view.findViewById(R.id.f2_send);
            f2Input = (EditText) view.findViewById(R.id.f2_input);
            f2Input.setText(sharedPreferences.getString("f2", ""));
            f2Button.setOnClickListener(this);

            dialogBuilder.setView(view);

            return dialogBuilder.create();
        }

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.f1_send:
                    Log.d("Tolong kirim", "ya");
                    ((MainActivity) getActivity()).sendMessage(f1Input.getText().toString());
                    break;
                case R.id.f2_send:
                    Log.d("Mohon kirim", "ya");
                    ((MainActivity) getActivity()).sendMessage(f2Input.getText().toString());
                    break;
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("f1", f1Input.getText().toString());
            editor.putString("f2", f2Input.getText().toString());
            editor.apply();
        }
    }

    public static class SettingsDialogFragment extends DialogFragment {

        private SharedPreferences sharedPreferences;
        private RadioGroup movementReference;
        private RadioGroup delaySetting;
        private RadioGroup timeoutSetting;
        private String movement = "robot";

        private int delay;
        private int timeout;

        public SettingsDialogFragment() {
        }

        public static SettingsDialogFragment newInstance() {
            return new SettingsDialogFragment();
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // setup dialog: buttons, title etc
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity())
                    .setTitle("Settings")
                    .setPositiveButton("Done",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    );

            LayoutInflater inflater = getActivity().getLayoutInflater();
            View view = inflater.inflate(R.layout.fragment_settings_dialog, null);

            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            delay = sharedPreferences.getInt("delay", 500);
            timeout = sharedPreferences.getInt("timeout", 10);

            RadioButton checkedMovement = (RadioButton) view.findViewById(sharedPreferences.getInt("movementCheckedId", R.id.movement_robot));
            checkedMovement.setChecked(true);
            RadioButton checkedDelay = (RadioButton) view.findViewById(sharedPreferences.getInt("delayCheckedId", R.id.delay_short));
            checkedDelay.setChecked(true);
            RadioButton checkedTimeout = (RadioButton) view.findViewById(sharedPreferences.getInt("timeoutCheckedId", R.id.timeout_long));
            checkedTimeout.setChecked(true);

            movementReference = (RadioGroup) view.findViewById(R.id.movement_reference);
            movementReference.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
                    switch (i) {
                        case R.id.movement_robot:
                            movement = "robot";
                            break;
                        case R.id.movement_arena:
                            movement = "arena";
                            break;
                    }
                }
            });

            delaySetting = (RadioGroup) view.findViewById(R.id.delay);
            delaySetting.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
                    switch (i) {
                        case R.id.delay_short:
                            delay = 500;
                            break;
                        case R.id.delay_medium:
                            delay = 750;
                            break;
                        case R.id.delay_long:
                            delay = 1000;
                            break;
                    }
                }
            });

            timeoutSetting = (RadioGroup) view.findViewById(R.id.timeout);
            timeoutSetting.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
                    switch (i) {
                        case R.id.timeout_short:
                            timeout = 2;
                            break;
                        case R.id.timeout_medium:
                            timeout = 5;
                            break;
                        case R.id.timeout_long:
                            timeout = 10;
                            break;
                    }
                }
            });

            dialogBuilder.setView(view);

            return dialogBuilder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("movement", movement);
            editor.putInt("movementCheckedId", movementReference.getCheckedRadioButtonId());
            editor.putInt("delay", delay);
            editor.putInt("delayCheckedId", delaySetting.getCheckedRadioButtonId());
            editor.putInt("timeout", timeout);
            editor.putInt("timeoutCheckedId", timeoutSetting.getCheckedRadioButtonId());
            editor.apply();
            ((MainActivity) getActivity()).refreshSettings();
        }
    }
}