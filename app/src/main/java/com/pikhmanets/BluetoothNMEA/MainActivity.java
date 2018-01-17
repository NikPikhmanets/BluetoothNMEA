package com.pikhmanets.BluetoothNMEA;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.pikhmanets.BluetoothNMEA.bluetooth.BluetoothService;
import com.pikhmanets.BluetoothNMEA.bluetooth.Constants;
import com.pikhmanets.BluetoothNMEA.bluetooth.DeviceList;

import static com.pikhmanets.BluetoothNMEA.bluetooth.BluetoothService.STATE_CONNECTED;
import static com.pikhmanets.BluetoothNMEA.bluetooth.BluetoothService.STATE_CONNECTING;
import static com.pikhmanets.BluetoothNMEA.bluetooth.BluetoothService.STATE_NONE;
import static com.pikhmanets.BluetoothNMEA.bluetooth.Constants.MESSAGE_DEVICE_NAME;
import static com.pikhmanets.BluetoothNMEA.bluetooth.Constants.MESSAGE_READ;
import static com.pikhmanets.BluetoothNMEA.bluetooth.Constants.MESSAGE_STATE_CHANGE;
import static com.pikhmanets.BluetoothNMEA.bluetooth.Constants.MESSAGE_TOAST;
import static com.pikhmanets.BluetoothNMEA.bluetooth.Constants.MESSAGE_WRITE;
import static com.pikhmanets.BluetoothNMEA.bluetooth.Constants.REQUEST_ENABLE_BT;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CONNECT_DEVICE = 1;

    boolean isEnableBt;
    final int offset = 0;
    final String regex = "\r\n";

    final Context mContext = this;

    private Menu mMenu;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBluetoothService = null;

    private ListView mConversationView;

    private String mConnectedDeviceName = null;
    private ArrayAdapter<String> mConversationArrayAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isEnableBt = false;
        setStatus(R.string.title_not_connected);
        mConversationView = findViewById(R.id.list_view);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            String toastText = "Bluetooth is not available";
            Toast.makeText(mContext, toastText, Toast.LENGTH_SHORT).show();
        }

        Button btConnect = findViewById(R.id.btConnect);
        btConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = "Еще не готово";
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mBluetoothAdapter == null)
            return;
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_ENABLE_BT);
        } else if (mBluetoothService == null) {
            setupService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothService != null) {
            mBluetoothService.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothService != null) {
            if (mBluetoothService.getState() == BluetoothService.STATE_NONE) {
                mBluetoothService.start();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mMenu = menu;
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.connect_device_bt);
        menuItem.setTitle(isEnableBt ? "Disconnect" : "Connect");
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect_device_bt:

                isEnableBt = !isEnableBt;

                if (isEnableBt) {
                    Intent intent = new Intent(mContext, DeviceList.class);
                    startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
                } else {
                    setTitleMenu(R.string.disconnect_device);
                    mBluetoothService.stop();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                    setTitleMenu(R.string.disconnect_device);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setupService();
                } else {
                    String toastText = "Bluetooth was not enabled";
                    Toast.makeText(mContext, toastText, Toast.LENGTH_SHORT).show();
                }
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuff = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuff);
                    mConversationArrayAdapter.add("Me: " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    String readMessage = new String(readBuff, offset, msg.arg1);

//                    String[] value = readMessage.split(regex);
//                    List<String> listMsg = new ArrayList<>();
//                    listMsg.addAll(Arrays.asList(value));


                    String TAG = "ROVER";

//                    for (String str : value) {
                        mConversationArrayAdapter.add(readMessage);
                        Log.i(TAG, readMessage);
//                    }
                    Log.e(TAG, "******************************************************************************************");
//                    String val = listMsg.get(1);


//                    mConversationArrayAdapter.add(readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(mContext, "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(mContext, msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                    setTitleMenu(R.string.disconnect_device);
                    break;
            }
        }
    };

    private void setTitleMenu(int id) {
        MenuItem menuItem = mMenu.findItem(R.id.connect_device_bt);
        menuItem.setTitle(id);
    }

    private void connectDevice(Intent data) {
        String address = data.getStringExtra(DeviceList.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mBluetoothService.connect(device);

    }

    private void setupService() {
        mConversationArrayAdapter = new ArrayAdapter<>(mContext, R.layout.message);
        mConversationView.setAdapter(mConversationArrayAdapter);

        mBluetoothService = new BluetoothService(mHandler);

    }

    private void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(resId);
        }
    }

    private void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(subTitle);
        }
    }
}
