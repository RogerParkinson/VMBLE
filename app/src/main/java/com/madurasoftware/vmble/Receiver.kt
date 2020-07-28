package com.madurasoftware.vmble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log

class Receiver : BroadcastReceiver() {

    private var handler: Handler? = null
    private val TAG = "Receiver"

    fun setHandler(handler: Handler) {
        this.handler = handler
    }

    private fun getHandler():Handler {
        return handler!!
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive ${intent.extras}")
        val status = intent.extras!!.getInt(BLEService.CONNECTION_STATUS,-100)
        val info = intent.extras!!.getString(CONNECTION_INFO,"")
        Log.d(TAG, "onReceive $status $info")
        getHandler().obtainMessage(CONNECTING_STATUS, status, -1, info)
            .sendToTarget()
    }
}