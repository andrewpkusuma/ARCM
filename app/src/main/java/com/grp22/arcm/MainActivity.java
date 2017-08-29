package com.grp22.arcm;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {

    private ResponseReceiver receiver;
    private TextView status;
    BluetoothConnectService mService;
    boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.status);

        Button stopButton = (Button) findViewById(R.id.stop_connection);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.stop();
            }
        });

        Button sendText = (Button) findViewById(R.id.send_text);
        sendText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.sendToOutputStream("Lorem Ipsum");
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

        ImageButton left = (ImageButton) findViewById(R.id.left);
        left.setOnTouchListener(new ControllerListener("left"));

        ImageButton right = (ImageButton) findViewById(R.id.right);
        right.setOnTouchListener(new ControllerListener("right"));

        ImageButton rotate = (ImageButton) findViewById(R.id.rotate);
        rotate.setOnTouchListener(new ControllerListener("rotate"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BluetoothConnectService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ResponseReceiver.DISCONNECT_SUCCESS);
        filter.addAction(ResponseReceiver.STRING_RECEIVED);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        receiver = new ResponseReceiver();
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }


    public class ResponseReceiver extends BroadcastReceiver {
        public static final String DISCONNECT_SUCCESS =
                "com.grp22.arcm.DISCONNECTION_SUCCESSFUL";
        public static final String STRING_RECEIVED =
                "com.grp22.arcm.STRING_RECEIVED";


        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ResponseReceiver.DISCONNECT_SUCCESS.equals(action)) {
                Toast.makeText(getApplicationContext(), "Disconnection successful", Toast.LENGTH_SHORT).show();
            }
            if (ResponseReceiver.STRING_RECEIVED.equals(action)) {
                String message = intent.getStringExtra("message");
                status.setText(message);
            }
        }
    }

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

    private class ControllerListener implements View.OnTouchListener {
        String command;

        public ControllerListener(String command) {
            this.command = command;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                mService.sendToOutputStream(command);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mService.sendToOutputStream("stop");
            }
            return true;
        }
    }
}
