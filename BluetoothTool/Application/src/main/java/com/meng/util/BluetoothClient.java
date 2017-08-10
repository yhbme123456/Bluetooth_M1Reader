package com.meng.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.example.android.common.logger.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static com.meng.util.Constants.STATE_CONNECTED;
import static com.meng.util.Constants.STATE_CONNECTING;
import static com.meng.util.Constants.STATE_NONE;

/**
 * Created by meng on 8/9/17.
 */

public class BluetoothClient {
    private String TAG = BluetoothClient.class.getSimpleName();
    private static final UUID uuid =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private int mState;
    private int mNewState;

    private TransferThread mTransferThread;
    /**
     * Constructor.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothClient(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }


    public int connect(BluetoothDevice device){
        if(mTransferThread==null){
            mTransferThread = new TransferThread(device);
            mTransferThread.start();
        }
        return 0;
    }
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class TransferThread extends Thread {
        private BluetoothSocket mmSocket;
        private BluetoothDevice mmDevice;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        public TransferThread(BluetoothDevice device) {
            mmDevice = device;
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mState = STATE_CONNECTING;
            updateUserInterfaceTitle();
        }

        public void run() {
            Log.i(TAG, "BEGIN TransferThread" );
            setName("TransferThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
                mmInStream = mmSocket.getInputStream();
                mmOutStream = mmSocket.getOutputStream();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }
            // Send the name of the connected device back to the UI Activity
            Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.DEVICE_NAME, mmDevice.getName());
            msg.setData(bundle);
            mHandler.sendMessage(msg);


            mState = STATE_CONNECTED;
            updateUserInterfaceTitle();
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

//            try {
//                mmInStream = mmSocket.getInputStream();
//                mmOutStream = mmSocket.getOutputStream();
//            }catch (IOException e2) {
//                Log.e(TAG, "unable to close() " + " socket during connection failure", e2);
//            }

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
//                    mmDevice.getBondState();
//                    if(!mmSocket.isConnected()){
//                        connectionLost();
//                        break;
//                    }
//                    // Read from the InputStream
//                    if(mmInStream.available()>0){
//                        bytes = mmInStream.read(buffer);
//                        Log.d(TAG,"接收到字节数="+bytes);
//                        // Send the obtained bytes to the UI Activity
//                        mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
//                                .sendToTarget();
//                    }else{
//                        SystemClock.sleep(100);
//                    }


                    bytes = mmInStream.read(buffer);
                    Log.d(TAG,"接收到字节数="+bytes);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
            Log.d(TAG,"线程退出");
//
//            // Reset the ConnectThread because we're done
//            synchronized (BluetoothChatService.this) {
//                mConnectThread = null;
//            }
//
//            // Start the connected thread
//            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmInStream.close();
                mmOutStream.close();
                mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "蓝牙设备连接失败");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        mTransferThread = null;
        // Start the service over to restart listening mode
//        BluetoothChatService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "蓝牙设备断开连接");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();
        mTransferThread = null;
//        // Start the service over to restart listening mode
//        BluetoothChatService.this.start();
    }
    /**
     * Update UI title according to the current state of the chat connection
     */
    private synchronized void updateUserInterfaceTitle() {
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mTransferThread != null) {
            mTransferThread.cancel();
            mTransferThread = null;
        }

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();
    }
}
