package com.madurasoftware.vmble

import android.app.Notification.*
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.*

class NotificationService : NotificationListenerService() {

    private val TAG = "NotificationService"
    private var mBluetoothDeviceAddress: String? = null//Address of the connected BLE device

    override fun onCreate() {
        super.onCreate()
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }
        Log.d(TAG, "onCreate() called")
    }

    override fun onStartCommand(intent: Intent, flags:Int, startId:Int):Int {
        Log.d(TAG, "onStartCommand")
        mBluetoothDeviceAddress = intent.getStringExtra(BLEService.CONNECTION)
        //setTime()
        return Service.START_NOT_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "listener connected")
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "listener disconnected")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "onNotificationPosted1")
        val map = sbn.notification.extras
        val category = sbn.notification.category ?: ""
        Log.d(TAG, "onNotificationPosted2 $category")
        when (category) {
            CATEGORY_EVENT-> {}
            CATEGORY_MESSAGE-> {}
            CATEGORY_ALARM-> {}
            CATEGORY_CALL-> {}
            CATEGORY_REMINDER-> {}
            else -> return
        }
        val title = map.getString(EXTRA_TITLE) ?: ""
        val text = (map.getString(EXTRA_TEXT) ?: "").trim()
        val message = "[$category][$title]$text"

        Log.d(TAG, "onNotificationPosted3 $message")
        val connectIntent = Intent(this.applicationContext, BLEService::class.java)
        connectIntent.putExtra(BLEService.MESSAGE,message)
        connectIntent.putExtra(BLEService.CONNECTION,mBluetoothDeviceAddress)
        this.applicationContext.startService(connectIntent)
        Log.d(TAG, "onNotificationPosted4 $message okay")
    }

    companion object
}