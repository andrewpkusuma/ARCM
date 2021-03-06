package com.grp22.arcm;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private final int REQ_CODE_SPEECH_INPUT = 69;
    private final SpeechCommandProcessor processor = new SpeechCommandProcessor();
    private final Object lock = new Object();
    private SharedPreferences sharedPreferences;
    private int delay;
    private boolean isArena;
    private ResponseReceiver receiver;
    private IntentFilter filter;
    private TextView status;
    private ImageButton forward;
    private ImageButton rotateLeft;
    private ImageButton rotateRight;
    private Button calibrate;
    private RelativeLayout header;
    private RelativeLayout footer;
    private LinearLayout actionModeTop;
    private RelativeLayout actionModeMid;
    private LinearLayout actionModeBottom;
    private Button refreshMap;
    private ToggleButton toggleRefresh;
    private BluetoothConnectService mService;
    private boolean mBound = false;
    private boolean isRegistered = false;
    private boolean[] allowManualUpdate = {false, false};
    private AlertDialog confirmExitDialog;
    private ProgressDialog progressDialog;
    private boolean isInterrupted;
    private boolean isPreviouslyRecovered;
    private int orientation = 0; // 0 = N, 1 = E, 2 = S, 3 = W
    private String mapDescriptor1;
    private String mapDescriptor2;
    private Integer[] list = new Integer[300];
    private GridView gridView;

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

    private ActionMode.Callback mCallback = new ActionMode.Callback() {
        MapAdapter mapAdapter;
        int currentWaypoint;
        int currentRobotPosition;

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTitle("Set Coordinates");
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.action_mode, menu);
            mapAdapter = (MapAdapter) gridView.getAdapter();
            currentWaypoint = mapAdapter.getWaypointPosition();
            currentRobotPosition = mapAdapter.getRobotPosition();
            toggleViewGroupVisibility(header, View.INVISIBLE);
            toggleViewGroupVisibility(footer, View.INVISIBLE);
            toggleViewGroupVisibility(actionModeTop, View.VISIBLE);
            toggleViewGroupVisibility(actionModeMid, View.VISIBLE);
            toggleViewGroupVisibility(actionModeBottom, View.VISIBLE);
            final ToggleButton wayPoint = (ToggleButton) findViewById(R.id.toggle_waypoint);
            final ToggleButton position = (ToggleButton) findViewById(R.id.toggle_robot_position);
            final ImageButton rotateLeft = (ImageButton) findViewById(R.id.set_rotate_left);
            final ImageButton rotateRight = (ImageButton) findViewById(R.id.set_rotate_right);
            mapAdapter.setSelectionMode(0);
            wayPoint.setChecked(true);
            position.setChecked(false);
            rotateLeft.setVisibility(View.INVISIBLE);
            rotateRight.setVisibility(View.INVISIBLE);
            wayPoint.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (b) {
                        rotateLeft.setVisibility(View.INVISIBLE);
                        rotateRight.setVisibility(View.INVISIBLE);
                        mapAdapter.setSelectionMode(0);

                    }
                }
            });
            wayPoint.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wayPoint.setChecked(true);
                    position.setChecked(false);
                }
            });
            position.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (b) {
                        rotateLeft.setVisibility(View.VISIBLE);
                        rotateRight.setVisibility(View.VISIBLE);
                        mapAdapter.setSelectionMode(1);
                    }
                }
            });
            position.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wayPoint.setChecked(false);
                    position.setChecked(true);
                }
            });
            rotateLeft.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mapAdapter.updateOrientation(false);
                }
            });
            rotateRight.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mapAdapter.updateOrientation(true);
                }
            });
            ((TextView) findViewById(R.id.waypoint)).setText(mapAdapter.getWaypointCoordinate());
            ((TextView) findViewById(R.id.robot_position)).setText(mapAdapter.getRobotCoordinate());
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            boolean ret = false;
            if (item.getItemId() == R.id.action_mode_save) {
                currentWaypoint = mapAdapter.getWaypointPosition();
                currentRobotPosition = mapAdapter.getRobotPosition();
                mode.finish();
                ret = true;
            } else if (item.getItemId() == R.id.action_mode_clear) {
                mapAdapter.setWaypointPosition(-100);
                mapAdapter.setRobotPosition(-100);
                mapAdapter.clearArrays();
                mapAdapter.notifyDataSetChanged();
                status.setText("STATUS");
            }
            return ret;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mapAdapter.setWaypointPosition(currentWaypoint);
            mapAdapter.setRobotPosition(currentRobotPosition);
            if (currentWaypoint != -100 && currentRobotPosition != -100) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mService.sendToOutputStream("pWP," + mapAdapter.getWaypointCoordinate().replaceAll("\\s+", ""));
                        SystemClock.sleep(delay);
                        mService.sendToOutputStream("pSP," + mapAdapter.getRobotCoordinate().replaceAll("\\s+", ""));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                forward.setEnabled(true);
                                rotateLeft.setEnabled(true);
                                rotateRight.setEnabled(true);
                                calibrate.setEnabled(true);
                            }
                        });
                    }
                }).start();
            }
            mapAdapter.setSelectionEnabled(false);
            toggleViewGroupVisibility(header, View.VISIBLE);
            toggleViewGroupVisibility(footer, View.VISIBLE);
            toggleViewGroupVisibility(actionModeTop, View.INVISIBLE);
            toggleViewGroupVisibility(actionModeMid, View.INVISIBLE);
            toggleViewGroupVisibility(actionModeBottom, View.INVISIBLE);
        }
    };

    private void toggleViewGroupVisibility(ViewGroup vg, int visibility) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            child.setVisibility(visibility);
            if (child instanceof ViewGroup) {
                toggleViewGroupVisibility((ViewGroup) child, visibility);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        refreshSettings();

        status = (TextView) findViewById(R.id.status);

        header = (RelativeLayout) findViewById(R.id.header);
        footer = (RelativeLayout) findViewById(R.id.footer);
        actionModeTop = (LinearLayout) findViewById(R.id.action_mode_top);
        toggleViewGroupVisibility(actionModeTop, View.INVISIBLE);
        actionModeMid = (RelativeLayout) findViewById(R.id.action_mode_mid);
        toggleViewGroupVisibility(actionModeMid, View.INVISIBLE);
        actionModeBottom = (LinearLayout) findViewById(R.id.action_mode_bottom);
        toggleViewGroupVisibility(actionModeBottom, View.INVISIBLE);

        final Button stopButton = (Button) findViewById(R.id.stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmExitDialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Confirm to Exit")
                        .setMessage("Are you sure to disconnect and return to device selection screen?")
                        .setPositiveButton("DISCONNECT", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Toast.makeText(getApplicationContext(), "Disconnecting...", Toast.LENGTH_SHORT).show();
                                mService.stop();
                                unbindService(mConnection);
                                mBound = false;
                            }
                        })
                        .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                if (isInterrupted && !progressDialog.isShowing())
                                    progressDialog.show();
                            }
                        })
                        .create();
                confirmExitDialog.show();
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

        gridView = (GridView) findViewById(R.id.map);
        MapAdapter adapter = new MapAdapter(MainActivity.this, R.layout.row_grid, list);
        gridView.setAdapter(adapter);

        Button setWaypoint = (Button) findViewById(R.id.set_waypoint);
        setWaypoint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapAdapter mapAdapter = (MapAdapter) gridView.getAdapter();
                mapAdapter.setSelectionEnabled(true);
                startActionMode(mCallback);
            }
        });

        refreshMap = (Button) findViewById(R.id.refresh_map);
        refreshMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allowManualUpdate[0] = true;
                allowManualUpdate[1] = true;
            }
        });

        toggleRefresh = (ToggleButton) findViewById(R.id.toggle_refresh);
        toggleRefresh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                refreshMap.setEnabled(!isChecked);
                if (isChecked)
                    refreshMap.getCompoundDrawables()[0].setAlpha(63);
                else
                    refreshMap.getCompoundDrawables()[0].setAlpha(255);
            }
        });

        forward = (ImageButton) findViewById(R.id.forward);
        forward.setOnTouchListener(new ControllerListener("forward"));
        forward.setEnabled(false);

        /*ImageButton reverse = (ImageButton) findViewById(R.id.reverse);
        reverse.getDrawable().setAlpha(0);

        ImageButton left = (ImageButton) findViewById(R.id.left);
        left.setOnTouchListener(new ControllerListener("left"));

        ImageButton right = (ImageButton) findViewById(R.id.right);
        right.setOnTouchListener(new ControllerListener("right"));*/

        rotateLeft = (ImageButton) findViewById(R.id.rotate_left);
        rotateLeft.setOnTouchListener(new ControllerListener("rotateLeft"));
        rotateLeft.setEnabled(false);

        rotateRight = (ImageButton) findViewById(R.id.rotate_right);
        rotateRight.setOnTouchListener(new ControllerListener("rotateRight"));
        rotateRight.setEnabled(false);

        calibrate = (Button) findViewById(R.id.calibrate);
        calibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.sendToOutputStream("hC");
            }
        });
        calibrate.setEnabled(false);

        Button exploreArena = (Button) findViewById(R.id.explore_arena);
        exploreArena.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.sendToOutputStream("pEX_START");
            }
        });

        Button fastestPath = (Button) findViewById(R.id.show_fastest_path);
        fastestPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.sendToOutputStream("pFP_START");
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
        progressDialog.setCancelable(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Disconnect Now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                stopButton.callOnClick();
            }
        });
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
            case R.id.info:
                View dialogView = getLayoutInflater().inflate(R.layout.fragment_info_dialog, null);
                ((TextView) dialogView.findViewById(R.id.string1)).setText(mapDescriptor1);
                ((TextView) dialogView.findViewById(R.id.string2)).setText(mapDescriptor2);
                dialogView.findViewById(R.id.copy_string).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("label", "Part one:\n\n" + mapDescriptor1 + "\n\nPart two:\n\n" + mapDescriptor2);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                        }
                        Toast.makeText(getApplicationContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                });
                new AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setPositiveButton("DONE", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
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
        if (!isRegistered) {
            receiver = new ResponseReceiver();
            registerReceiver(receiver, filter);
            Log.d("Registered", "woo-hoo!");
            isRegistered = true;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (refreshMap.getCompoundDrawables()[0] == null) {
                    SystemClock.sleep(10);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toggleRefresh.setChecked(true);
                    }
                });
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        ((Button) findViewById(R.id.stop)).callOnClick();
    }

    @Override
    protected void onDestroy() {
        mService.stop();
        if (isRegistered) {
            unregisterReceiver(receiver);
            Log.d("Unregistered", "d'oh!");
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
        String[] commandList = {"hINSTR;F", "hINSTR;L", "reverse", "hINSTR;R"};

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
                Toast.makeText(this, "Command not recognized", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleStringInput(String input) {
        String[] separated = input.split("\\r\\n|\\n|\\r"); // in case the input overflows the stream and are combined as one
        for (String s : separated) {
            synchronized (lock) {
                Log.d("Terpisahkan", s);
                String[] inputSplitted = s.split("\\s+");
                switch (inputSplitted[0]) {
                    case "MAP":
                        if (toggleRefresh.isChecked() || allowManualUpdate[1]) {
                            allowManualUpdate[1] = false;
                            mapDescriptor1 = inputSplitted[1];
                            mapDescriptor2 = inputSplitted[2];
                            updateMap();
                        }
                        break;
                    case "BOT_POS":
                        String positionString = inputSplitted[1];
                        String[] positionStrings = positionString.split(",");
                        switch (positionStrings[2]) {
                            case "N":
                                orientation = 0;
                                break;
                            case "E":
                                orientation = 1;
                                break;
                            case "W":
                                orientation = 3;
                                break;
                            case "S":
                                orientation = 2;
                                break;
                        }
                        Log.d("Orientation", Integer.toString(orientation));
                        if (toggleRefresh.isChecked() || allowManualUpdate[0]) {
                            allowManualUpdate[0] = false;
                            MapAdapter mapAdapter = (MapAdapter) gridView.getAdapter();
                            try {
                                mapAdapter.setRobotPosition((19 - Integer.parseInt(positionStrings[0])) * 15 + (Integer.parseInt(positionStrings[1])));
                                mapAdapter.notifyDataSetChanged();
                            } catch (NumberFormatException n) {
                                Log.d("What", "the hell");
                            }
                        }
                        break;
                    default:
                        String statusText = inputSplitted[0].split(";")[1];
                        switch (statusText) {
                            case "F":
                                status.setText("MOVING FORWARD");
                                break;
                            case "L":
                                status.setText("TURNING LEFT");
                                break;
                            case "R":
                                status.setText("TURNING RIGHT");
                                break;
                            case "A":
                                status.setText("CALIBRATING");
                                break;
                            case "E":
                                status.setText("REVERSING");
                                break;
                            default:
                                try {
                                    int numberOfTimes = Integer.parseInt(statusText);
                                    if (numberOfTimes == 0)
                                        status.setText("STOP");
                                    else if (numberOfTimes == 1)
                                        status.setText("MOVING FORWARD");
                                    else
                                        status.setText("FORWARD " + numberOfTimes + " TIMES");
                                } catch (NumberFormatException n) {
                                    Log.d("What", "the f");
                                }
                        }
                        break;
                }
            }
        }
    }

    private void updateMap() {
        Log.d("Pembaharuan", "dimulai");
        String mapDescriptor1Raw = MapDecoder.decode(mapDescriptor1, true);
        String mapDescriptor2Raw = MapDecoder.decode(mapDescriptor2, false);

        MapAdapter mapAdapter = (MapAdapter) gridView.getAdapter();
        mapAdapter.clearArrays();

        int index = 0;
        for (int i = 0; i < mapDescriptor1Raw.length(); i++) {
            char c = mapDescriptor1Raw.charAt(i);
            if (c == '1') {
                mapAdapter.markExplored((19 - i / 15) * 15 + (i % 15));
                char ch = mapDescriptor2Raw.charAt(index);
                if (ch == '1')
                    mapAdapter.markObstacle((19 - i / 15) * 15 + (i % 15));
                index++;
            }
        }
        mapAdapter.notifyDataSetChanged();
    }

    public void refreshSettings() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String movement = sharedPreferences.getString("movement", "robot");
        if (movement.equals("robot"))
            isArena = false;
        Log.d("isArena", String.valueOf(isArena));
        delay = sharedPreferences.getInt("delay", 500);
        Log.d("delay", Integer.toString(delay));
        Log.d("timeout", Integer.toString(sharedPreferences.getInt("timeout", 10)));
        if (mBound)
            mService.setTimeout(sharedPreferences.getInt("timeout", 10));
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
        private String movement;

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
            movement = sharedPreferences.getString("movement", "robot");
            delay = sharedPreferences.getInt("delay", 500);
            timeout = sharedPreferences.getInt("timeout", 10);

            ((RadioButton) ((RadioGroup) view.findViewById(R.id.movement_reference)).findViewById(R.id.movement_robot)).setChecked(true);

            switch (delay) {
                case 500:
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.delay)).findViewById(R.id.delay_short)).setChecked(true);
                    break;
                case 750:
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.delay)).findViewById(R.id.delay_medium)).setChecked(true);
                    break;
                case 100:
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.delay)).findViewById(R.id.delay_long)).setChecked(true);
                    break;
            }

            switch (timeout) {
                case 2:
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.timeout)).findViewById(R.id.timeout_short)).setChecked(true);
                    break;
                case 5:
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.timeout)).findViewById(R.id.timeout_medium)).setChecked(true);
                    break;
                case 10:
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.timeout)).findViewById(R.id.timeout_long)).setChecked(true);
                    break;
            }

            movementReference = (RadioGroup) view.findViewById(R.id.movement_reference);
            movementReference.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
                    switch (i) {
                        case R.id.movement_robot:
                            movement = "robot";
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
            editor.putInt("delay", delay);
            editor.putInt("timeout", timeout);
            editor.apply();
            ((MainActivity) getActivity()).refreshSettings();
        }
    }

    public class ResponseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothConnectService.CONNECTION_INTERRUPTED:
                    isInterrupted = true;
                    isPreviouslyRecovered = false;
                    progressDialog.show();
                    break;
                case BluetoothConnectService.CONNECTION_RECOVERED:
                    isInterrupted = false;
                    if (!isPreviouslyRecovered) {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Connection recovered", Toast.LENGTH_SHORT).show();
                        isPreviouslyRecovered = true;
                    }
                    break;
                case BluetoothConnectService.DISCONNECTED:
                    isInterrupted = false;
                    if (progressDialog.isShowing())
                        progressDialog.dismiss();
                    if (confirmExitDialog != null && confirmExitDialog.isShowing())
                        confirmExitDialog.dismiss();
                    Log.d("T e r", "p a n g g i l");
                    Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
                    finish();
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
                ((ImageButton) view).getDrawable().setColorFilter(ContextCompat.getColor(getApplicationContext(), android.R.color.white), PorterDuff.Mode.SRC_ATOP);
                view.getBackground().setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(getApplicationContext(), android.R.color.holo_blue_light), PorterDuff.Mode.SRC_ATOP));
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
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.getBackground().clearColorFilter();
                view.getBackground().invalidateSelf();
                ((ImageButton) view).getDrawable().setColorFilter(ContextCompat.getColor(getApplicationContext(), android.R.color.black), PorterDuff.Mode.SRC_ATOP);
                sendCommandThread.interrupt();
            }
            return true;
        }
    }

    public class MapAdapter extends ArrayAdapter {
        Context context;
        Object[] items;
        int resource;
        int waypointPosition = -100;
        int robotPosition = -100;
        ArrayList<Integer> explored = new ArrayList<>();
        ArrayList<Integer> obstacle = new ArrayList<>();

        LayoutInflater inflater;

        boolean selectionEnabled = false;
        int toggleSelection = 0; // 0 = waypoint, 1 = robot position

        public MapAdapter(Context context, int resource, Object[] items) {
            super(context, resource, items);
            this.context = context;
            this.resource = resource;
            this.items = items;

            inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setSelectionEnabled(boolean selectionEnabled) {
            this.selectionEnabled = selectionEnabled;
        }

        public void setSelectionMode(int mode) {
            this.toggleSelection = mode;
        }

        public int getWaypointPosition() {
            return this.waypointPosition;
        }

        public int getRobotPosition() {
            return this.robotPosition;
        }

        public void setWaypointPosition(int position) {
            this.waypointPosition = position;
            ((TextView) findViewById(R.id.waypoint)).setText(getWaypointCoordinate());
            notifyDataSetChanged();
        }

        public void setRobotPosition(int position) {
            this.robotPosition = position;
            ((TextView) findViewById(R.id.robot_position)).setText(getRobotCoordinate());
            notifyDataSetChanged();
        }

        public String getWaypointCoordinate() {
            if (waypointPosition > 0)
                return (19 - waypointPosition / 15) + ", " + (waypointPosition % 15);
            else
                return "N/A";
        }

        public String getRobotCoordinate() {
            if (robotPosition > 0) {
                String orientationString = "";
                switch (orientation) {
                    case 0:
                        orientationString = "N";
                        break;
                    case 1:
                        orientationString = "E";
                        break;
                    case 2:
                        orientationString = "S";
                        break;
                    case 3:
                        orientationString = "W";
                        break;
                }
                return (19 - robotPosition / 15) + ", " + (robotPosition % 15) + ", " + orientationString;
            } else
                return "N/A";
        }

        public void updateOrientation(boolean toggle) {
            synchronized (lock) {
                if (toggle)
                    orientation = (orientation + 1) % 4;
                else
                    orientation = (orientation - 1) % 4;
                ((TextView) findViewById(R.id.robot_position)).setText(getRobotCoordinate());
                notifyDataSetChanged();
            }
        }

        private void markExplored(int position) {
            explored.add(position);
        }

        private void markObstacle(int position) {
            obstacle.add(position);
        }

        private void clearArrays() {
            explored.clear();
            obstacle.clear();
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public Object getItem(int position) {
            return items[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                // If convertView is null then inflate the appropriate layout file
                convertView = inflater.inflate(resource, null);
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (selectionEnabled && (!(position % 15 == 0 || position % 15 == 14 || position / 15 == 0 || position / 15 == 19))) {
                        if (toggleSelection == 0)
                            setWaypointPosition(position);
                        else if (toggleSelection == 1)
                            setRobotPosition(position);
                    }
                }
            });

            ImageView robotOrientation = (ImageView) convertView.findViewById(R.id.cell_image);
            robotOrientation.setImageResource(0);

            // Layer 1: Obstacle/empty/unexplored
            if (obstacle.contains(position))
                convertView.setBackgroundResource(R.drawable.obstacle);
            else if (explored.contains(position))
                convertView.setBackgroundResource(R.drawable.empty);
            else
                convertView.setBackgroundResource(R.drawable.empty_unexplored);

            // Layer 2: Home & goal
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (position == ((20 - 1) - i) * 15 + j)
                        convertView.setBackgroundResource(R.drawable.home);
                    else if (position == (i * 15 + (14 - j)))
                        convertView.setBackgroundResource(R.drawable.goal);
                }
            }

            // Layer 3: Waypoint & Robot
            for (int i = -1; i < 2; i++) {
                for (int j = -1; j < 2; j++) {
                    if (position == (waypointPosition - i * 15) + j)
                        convertView.setBackgroundResource(R.drawable.way_point);
                }
            }
            for (int i = -1; i < 2; i++) {
                for (int j = -1; j < 2; j++) {
                    if (position == (robotPosition - i * 15) + j)
                        convertView.setBackgroundResource(R.drawable.robot);
                }
            }
            if (position == robotPosition) {
                robotOrientation.setImageResource(R.drawable.ic_navigation_white_48px);
                robotOrientation.setRotation(orientation * 90);
            }

            // Set height and width constraints for the image view
            //convertView.setLayoutParams(new LinearLayout.LayoutParams(pixels, pixels));
            return convertView;
        }
    }
}