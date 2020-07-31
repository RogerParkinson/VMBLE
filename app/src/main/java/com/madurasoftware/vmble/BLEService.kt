package com.madurasoftware.vmble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.appcompat.app.AppCompatActivity

const val ACTION = "CONNECTION_UPDATE"
const val MESSAGE = "MESSAGE"
const val CONNECTION_INFO = "ConnectionInfo"
// used in bluetooth handler to identify message status
const val CONNECTING_STATUS = 3
const val CONNECTING_STATUS_FAILED = -1
const val CONNECTING_STATUS_CONNECTED = 1
const val CONNECTING_STATUS_CONNECTING = 2
const val CONNECTING_STATUS_DISCONNECTING = 3
const val CONNECTING_STATUS_DISCONNECTED = 4
const val CONNECTING_STATUS_NO_ADDRESS = 5

class BLEService: Service() {

    private val TAG = "BLEService"
    private val mBinder = LocalBinder()//Binder for Activity that binds to this Service
    private var mBluetoothManager: BluetoothManager? = null//BluetoothManager used to get the BluetoothAdapter
    private var mBluetoothGatt: BluetoothGatt? = null//BluetoothGatt controls the Bluetooth communication link
    private var mBluetoothDeviceAddress: String? = null//Address of the connected BLE device
    private val sendQueue: Queue<String>? = ConcurrentLinkedQueue<String>() //To be inited with sendQueue = new ConcurrentLinkedQueue<String>();
    @Volatile
    private var isWriting: Boolean = false
    private var characteristic: BluetoothGattCharacteristic? = null
    private val MAX_MESSAGE_SIZE = 19

    private val targetUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {//Change in connection state

            if (newState == BluetoothProfile.STATE_CONNECTED) {//See if we are connected
                Log.i(TAG, "onConnectionStateChange connected $newState")
                gatt.discoverServices()
                mBluetoothGatt?.discoverServices()//Discover services on connected BLE device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//See if we are not connected
                Log.i(TAG, "onConnectionStateChange disconnected $newState")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {              //BLE service discovery complete
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the service discovery was successful
                Log.i(TAG, "onServicesDiscovered success: $status")
                figureCharacteristic(gatt)
            } else {                                                                     //Service discovery failed so log a warning
                Log.i(TAG, "onServicesDiscovered failed: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { //A request to Read has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //See if the read was successful
                Log.i(TAG, "onCharacteristicRead OK: $characteristic")
            } else {
                Log.i(TAG, "onCharacteristicRead: Error$status")
            }
        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) { //A request to Write has completed
            super.onCharacteristicWrite(gatt, characteristic, status)
            isWriting = false
            send()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic != null && characteristic.properties == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
                Log.e(TAG, "onCharacteristicChanged")
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        Log.d(TAG, "onCreate() called")
        //NotificationQueue.add("starting")
    }

    override fun onDestroy() {
//        baseContext.unregisterReceiver(this.messageReceiver)
    }

    override fun onStartCommand(intent: Intent, flags:Int, startId:Int):Int {
        Log.d(TAG, "onStartCommand")
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        val message = intent.getStringExtra(MESSAGE)
        if (message !=null) {
            NotificationQueue.add(message)
        }
        var address = intent.getStringExtra(CONNECTION)
        Log.d(TAG, "onStartCommand with address $address message $message existing BluetoothAddress $mBluetoothDeviceAddress ")
        if (address == null) {
            address = mBluetoothDeviceAddress
        }
        if (address == null) {
            broadcastMessage(CONNECTING_STATUS_NO_ADDRESS,"","no-address")
            return Service.START_NOT_STICKY
        }

        var count = 0;

        while (connect(address)) {
            count++
            Log.d(TAG, "connect executed $count times")
            characteristic = figureCharacteristic(mBluetoothGatt!!)
            while (characteristic == null && count < 5) {
                Thread.sleep(1000)
                characteristic = figureCharacteristic(mBluetoothGatt!!)
                count++
            }
            if (characteristic != null && !processMessages()) {
                if (mBluetoothGatt != null) {                                                   //Check for existing BluetoothGatt connection
                    mBluetoothGatt!!.close()                                                     //Close BluetoothGatt coonection for proper cleanup
                    mBluetoothGatt = null    //@@                                                  //No longer have a BluetoothGatt connection
                }
                //broadcastMessage(CONNECTING_STATUS_DISCONNECTED,address)
                break
            }
        }
        Log.d(TAG, "onStartCommand exiting")
        return Service.START_NOT_STICKY
    }

    private fun figureCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                if (characteristic.uuid == targetUUID) {
                    Log.d(TAG, "figureCharacteristic found uuid=${characteristic.uuid}")
                    return characteristic
                }
            }
        }
        Log.d(TAG, "figureCharacteristic found null")
        return null
    }

    // An activity has bound to this service
    override fun onBind(intent: Intent): IBinder? {
        val message = intent.getStringExtra("message")
        Log.d(TAG, "onBind $message")
        return null                                                                 //Return LocalBinder when an Activity binds to this Service
    }

//    // An activity has unbound from this service
    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind ")
//
////        if (mBluetoothGatt != null) {                                                   //Check for existing BluetoothGatt connection
////            mBluetoothGatt!!.close()                                                     //Close BluetoothGatt coonection for proper cleanup
////            mBluetoothGatt = null                                                      //No longer have a BluetoothGatt connection
////        }
//
        return super.onUnbind(intent)
    }

    private fun broadcastMessage(message:Int, info:String, comment:String) {
        val intent = Intent()
        intent.action = ACTION
        intent.flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
        intent.putExtra(CONNECTION_STATUS,message)
        intent.putExtra(CONNECTION_INFO,info)
        Log.d(TAG, "broadcasting ${CONNECTION_STATUS} $comment $message $info")
        sendBroadcast(intent)
    }

    private fun getBluetoothAdapter(): BluetoothAdapter {
        //Log.d(TAG, "getBluetoothAdapter")
        var bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        while (bluetoothAdapter == null) {
            Log.d(TAG, "getBluetoothAdapter")
            Thread.sleep(5000)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        }
        return bluetoothAdapter
    }

    private fun processMessages(): Boolean {
        Log.d(TAG, "processMessages: start")
        while (!NotificationQueue.isEmpty()) {
            val message = NotificationQueue.take()// this will block until a message arrives
            Log.d(TAG, "dequeued message $message")
            if (message.contains("[poison]", false)) {
                return false // poison value found, terminate the loop
            }
            if (!sendMessage("$message~")) {
                Log.d(TAG, "processMessages: exit w error?")
                return true // tell caller to retry connection
            }
            Log.d(TAG, "processMessages: loop")
        }
        return false
    }

    private fun send(): Boolean {
        if (sendQueue!!.isEmpty()) {
            Log.d("TAG", "_send(): EMPTY QUEUE")
            return false
        }
        val sending = sendQueue.poll()
        Log.d(TAG, "_send(): Sending: $sending")
        characteristic!!.value = sending.toByteArray(Charset.forName("UTF-8"))
        isWriting = true // Set the write in progress flag
        mBluetoothGatt!!.writeCharacteristic(characteristic)
        return true
    }

    private fun sendMessage(message: String):Boolean {
        Log.d(TAG, "sendMessage $message")
        var data = message
        while (data.length > MAX_MESSAGE_SIZE) {
            sendQueue!!.add(data.substring(0, MAX_MESSAGE_SIZE))
            data = data.substring(MAX_MESSAGE_SIZE)
        }
        sendQueue!!.add(data)
        if (!isWriting) send()
        return true //0
    }

    private fun connect(address: String): Boolean {
        broadcastMessage(CONNECTING_STATUS_CONNECTING,address,"connecting")
        try {
            mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                Log.d(TAG, "Bluetooth Manager is null")
                broadcastMessage(CONNECTING_STATUS_FAILED,address,"failed")
                return false
            }
            val bluetoothAdapter = getBluetoothAdapter()
            // Previously connected device.  Try to reconnect.
            if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
                //See if there was previous connection to the device
                Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.")
                //See if we can connect with the existing BluetoothGatt to connect
                //Success
                //Were not able to connect
                val ret = mBluetoothGatt!!.connect()
                if (ret) {
                    Log.d(TAG, "Connected existing.")
                    broadcastMessage(CONNECTING_STATUS_CONNECTED,address,"connected")
                } else {
                    Log.d(TAG, "failed to connect.")
                    broadcastMessage(CONNECTING_STATUS_FAILED,address,"failed")
                }
                return ret
            }
            Log.d(TAG, "getting device for new connection")
            val device = bluetoothAdapter.getRemoteDevice(address)
            if (device == null) {
                //Check whether a device was returned
                Log.d(TAG, "failed to connect.")
                broadcastMessage(CONNECTING_STATUS_FAILED, address,"failed")
                return false      //Failed to find the device
            }
            //No previous device so get the Bluetooth device by referencing its address
            Log.d(TAG, "getting gatt for new connection")
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback)                //Directly connect to the device so autoConnect is false
            mBluetoothDeviceAddress = address                                              //Record the address in case we need to reconnect with the existing BluetoothGatt
            if (mBluetoothGatt != null) {
                Log.d(TAG, "Connected for new connection")
                broadcastMessage(CONNECTING_STATUS_CONNECTED,address,"connected")
            } else {
                Log.d(TAG, "failed to connect.")
                broadcastMessage(CONNECTING_STATUS_FAILED,address,"failed")
            }
            return true
        } catch (e: Exception) {
            Log.i(TAG, e.message)
        }

        return false
    }

    // A Binder to return to an activity to let it bind to this service
    inner class LocalBinder : Binder(){
        internal fun getService(): BLEService {
            return this@BLEService//Return this instance of BluetoothLeService so clients can call its public methods
        }
    }

    companion object {
        private const val CHANNEL_ID = "Testing notifications ###"
        const val MESSAGE = "Message"
        const val MESSAGE_ACTION = "com.madurasoftware.vmnotifications.services.MessageAction"
        const val CONNECTION = "Connection"
        const val CONNECTION_STATUS = "Connection Status"
    }
}