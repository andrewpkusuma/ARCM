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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import static com.grp22.arcm.R.drawable.robot;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private int delay;
    private boolean isArena;
    private ResponseReceiver receiver;
    private IntentFilter filter;
    private TextView status;
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
    private ProgressDialog progressDialog;
    private boolean isPreviouslyRecovered;
    private final int REQ_CODE_SPEECH_INPUT = 69;
    private int orientation = 0; // 0 = up, 1 = right, 2 = down, 3 = left
    private final SpeechCommandProcessor processor = new SpeechCommandProcessor();
    private final Object lock = new Object();
    private ArrayList<String> mapDescriptor;
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
        boolean startingPositionSet = false;

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
                    startingPositionSet = true;
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
                mode.finish();
                ret = true;
            } else if (item.getItemId() == R.id.action_mode_clear) {
                mapAdapter.setWaypointPosition(-1);
            }
            return ret;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mapAdapter.setWaypointPosition(currentWaypoint);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (startingPositionSet) {
                        String startingCoordinate = mapAdapter.getRobotCoordinate();
                        if (startingCoordinate.equals("N/A"))
                            mService.sendToOutputStream("SP, -1, -1");
                        else
                            mService.sendToOutputStream("SP, " + startingCoordinate);
                    }
                    startingPositionSet = false;
                    SystemClock.sleep(delay);
                    String waypointCoordinate = mapAdapter.getWaypointCoordinate();
                    if (currentWaypoint == -1)
                        mService.sendToOutputStream("WP, -1, -1");
                    else
                        mService.sendToOutputStream("WP, " + waypointCoordinate);
                }
            }).start();
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

        mapDescriptor = new ArrayList<>();

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
                mService.sendToOutputStream("sendArenaInfo");
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
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Disconnect Now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                stopButton.callOnClick();
                dialogInterface.dismiss();
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
                if (toggleRefresh.isChecked() || allowManualUpdate[0]) {
                    allowManualUpdate[0] = false;
                    MapAdapter mapAdapter = (MapAdapter) gridView.getAdapter();
                    mapAdapter.setRobotPosition((20 - positions[1]) * 15 + (positions[0] - 1));
                    mapAdapter.notifyDataSetChanged();
                }
            } else if (inputJsonObject.has("grid") && (toggleRefresh.isChecked() || allowManualUpdate[1])) {
                allowManualUpdate[1] = false;
                synchronized (lock) {
                    mapDescriptor.add("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
                    mapDescriptor.add(inputJsonObject.getString("grid"));
                    if (mapDescriptor.size() == 2) {
                        mapDescriptor1 = mapDescriptor.get(0);
                        mapDescriptor2 = mapDescriptor.get(1);
                        updateMap();
                        status.setText("MAP UPDATED");
                        mapDescriptor.clear();
                    }
                }
            } else if (inputJsonObject.has("status")) {
                String statusText = inputJsonObject.getString("status");
                status.setText(statusText.toUpperCase());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateMap() {
        String mapDescriptor1Raw = MapDecoder.decode(mapDescriptor1, true);
        String mapDescriptor2Raw = MapDecoder.decode(mapDescriptor2, false);

        MapAdapter mapAdapter = (MapAdapter) gridView.getAdapter();

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
        int waypointPosition = -1;
        int robotPosition = -100;
        ArrayList<Integer> explored = new ArrayList<>();
        ArrayList<Integer> obstacle = new ArrayList<>();

        final float scale = getContext().getResources().getDisplayMetrics().density;
        int pixels = (int) (30 * scale + 0.5f);

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

        public int getSelectionMode() {
            return this.toggleSelection;
        }

        public int getWaypointPosition() {
            return this.waypointPosition;
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
                return (waypointPosition % 15 + 1) + ", " + (20 - waypointPosition / 15);
            else
                return "N/A";
        }

        public String getRobotCoordinate() {
            if (robotPosition > 0)
                return (robotPosition % 15 + 1) + ", " + (20 - robotPosition / 15) + ", " + orientation * 90;
            else
                return "N/A";
        }

        public void updateOrientation(boolean toggle) {
            if (toggle)
                orientation = (orientation + 1) % 4;
            else
                orientation = (orientation - 1) % 4;
            ((TextView) findViewById(R.id.robot_position)).setText(getRobotCoordinate());
            notifyDataSetChanged();
        }

        private void markExplored(int position) {
            explored.add(position);
        }

        private void markObstacle(int position) {
            obstacle.add(position);
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
                    if (selectionEnabled) {
                        if (toggleSelection == 0) {
                            setWaypointPosition(position);
                        } else if (toggleSelection == 1) {
                            if (!(position % 15 > 12 || position / 15 < 2)) {
                                setRobotPosition(position);
                            }
                        }
                    }
                }
            });

            ImageView robotOrientation = (ImageView) convertView.findViewById(R.id.cell_image);
            robotOrientation.setImageResource(0);

            // Layer 1: Obstacle/empty/unexploted
            if (obstacle.contains(position))
                convertView.setBackgroundResource(R.drawable.obstacle);
            else if (explored.contains(position))
                convertView.setBackgroundResource(R.drawable.empty);
            else
                convertView.setBackgroundResource(R.drawable.empty_unexplored);

            // Layer 2: Waypoint, home & goal
            if (position == waypointPosition)
                convertView.setBackgroundResource(R.drawable.way_point);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (position == ((20 - 1) - i) * 15 + j)
                        convertView.setBackgroundResource(R.drawable.home);
                    else if (position == (i * 15 + (14 - j)))
                        convertView.setBackgroundResource(R.drawable.goal);
                }
            }

            // Layer 3: Robot
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (position == (robotPosition - i * 15) + j)
                        convertView.setBackgroundResource(robot);
                }
            }
            if (position == robotPosition - 14) {
                robotOrientation.setImageResource(R.drawable.ic_navigation_white_48px);
                robotOrientation.setRotation(orientation * 90);
            }

            // Set height and width constraints for the image view
            //convertView.setLayoutParams(new LinearLayout.LayoutParams(pixels, pixels));
            return convertView;
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

            switch (movement) {
                case "robot":
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.movement_reference)).findViewById(R.id.movement_robot)).setChecked(true);
                    break;
                case "arena":
                    ((RadioButton) ((RadioGroup) view.findViewById(R.id.movement_reference)).findViewById(R.id.movement_arena)).setChecked(true);
                    break;
            }

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
            editor.putInt("delay", delay);
            editor.putInt("timeout", timeout);
            editor.apply();
            ((MainActivity) getActivity()).refreshSettings();
        }
    }
}