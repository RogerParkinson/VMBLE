package com.madurasoftware.vmble

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService

private const val TAG = "Popup"

class Popup(private val activity: Activity) {

    @SuppressLint("InflateParams")
    fun showPopup(anchorView: View) {

        val layoutinflator = activity.layoutInflater
        val popupView = layoutinflator.inflate(R.layout.paired_popup, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val btArrayAdapter = ArrayAdapter<DeviceWrapper>(activity, android.R.layout.simple_list_item_1)
        val devicesListView = popupView.findViewById(R.id.devicesListView) as ListView
        devicesListView.adapter = btArrayAdapter // assign model to view
        devicesListView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, arg3 ->
            popupWindow.dismiss()
            if (!isBluetoothSupported()) {
                Toast.makeText(activity, R.string.bluetooth_not_enabled, Toast.LENGTH_SHORT).show()
                return@OnItemClickListener
            }
            val d = btArrayAdapter.getItem(position) as DeviceWrapper
            connectToAddress(anchorView.context,d)
            configureNotification(anchorView.context,d)

        }
        if (!(listPairedDevices(activity, btArrayAdapter))) {
            return
        }

        // If the PopupWindow should be focusable
        popupWindow.isFocusable = true

        // If you need the PopupWindow to dismiss when when touched outside
        popupWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        popupWindow.setTouchInterceptor(View.OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                popupWindow.dismiss()
                return@OnTouchListener true
            }
            false
        })
        popupWindow.isOutsideTouchable = true
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0,0)
    }
    private fun configureNotification(context: Context, d:DeviceWrapper) {
        val notificationIntent = Intent(context, NotificationService::class.java)
        notificationIntent.putExtra(BLEService.CONNECTION,d.address)
        context.startService(notificationIntent)
        Log.d(TAG, "onStartCommand NotificationService started")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = "my channel"
            val descriptionText = "my description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(MainActivity.CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = context.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }
}