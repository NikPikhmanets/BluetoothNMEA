package com.pikhmanets.BluetoothNMEA.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {

    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    private static final UUID UUID_KEY = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
//    private static final UUID UUID_KEY = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final Handler mHandler;
    private final BluetoothAdapter mBluetoothAdapter;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private int mState;
    private int mNewState;

    public BluetoothService(Handler handler) {
        mHandler = handler;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mNewState = mState;
    }

    private synchronized void updateUserInterfaceTitle() {
        mState = getState();
        mNewState = mState;
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        updateUserInterfaceTitle();
    }

    public synchronized void connect(BluetoothDevice device) {

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();

        updateUserInterfaceTitle();
    }

    private synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        updateUserInterfaceTitle();
    }

    public synchronized void stop() {

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mState = STATE_NONE;

        updateUserInterfaceTitle();
    }

    public void write(byte[] out) {

        ConnectedThread r;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    private void connectionFailed() {
        Message message = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        message.setData(bundle);
        mHandler.sendMessage(message);

        mState = STATE_NONE;
        updateUserInterfaceTitle();

        BluetoothService.this.start();
    }

    private void connectionLost() {
        Message message = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        message.setData(bundle);
        mHandler.sendMessage(message);

        mState = STATE_NONE;
        updateUserInterfaceTitle();

        BluetoothService.this.start();
    }

    private class ConnectThread extends Thread {

        private BluetoothSocket mSocket;
        private BluetoothDevice mDevice;
        private String mSocketType;

        ConnectThread(BluetoothDevice device) {
            mDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = "Insecure";

            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_KEY);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            setName("ConnectThread" + mSocketType);
            mBluetoothAdapter.cancelDiscovery();

            try {
//                mSocket = (BluetoothSocket) mDevice.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class).invoke(mDevice, 1);
                mSocket.connect();
            } catch (IOException /*| IllegalAccessException | InvocationTargetException | NoSuchMethodException*/ e) {
                e.printStackTrace();

                try {
                    mSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                connectionFailed();
                return;
            }
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }
            connected(mSocket, mDevice, mSocketType);
        }

        void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {

        private final BluetoothSocket mBluetoothSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;

        ConnectedThread(BluetoothSocket socket) {
            mBluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mInputStream = tmpIn;
            mOutputStream = tmpOut;
            mState = STATE_CONNECTED;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (mState == STATE_CONNECTED) {
                try {
                    bytes = mInputStream.read(buffer);
                    String string = new String(buffer);
                    Log.e("ROVER", string);

                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionLost();
                    break;
                }
            }
        }

        void write(byte[] buffer) {
            try {
                mOutputStream.write(buffer);
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void cancel() {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
