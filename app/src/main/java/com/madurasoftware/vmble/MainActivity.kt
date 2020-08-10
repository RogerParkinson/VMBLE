package com.madurasoftware.vmble

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private var popup = Popup(this)
    private val receiver = Receiver()

    class MyHandler(private val mReadBuffer: TextView, private val mBluetoothStatus: TextView, private val button: Button) : Handler() {
        override fun handleMessage(msg: android.os.Message) {
            if (msg.what == MESSAGE_READ) {
                var readMessage: String? = null
                try {
                    readMessage = String(msg.obj as ByteArray, StandardCharsets.UTF_8)
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }

                mReadBuffer.text = readMessage
            }

            if (msg.what == CONNECTING_STATUS) {
                button.isEnabled = false
                button.isClickable = false
                when (msg.arg1) {
                    -1-> mBluetoothStatus.setText(R.string.connection_failed)
                    1-> {
                        mBluetoothStatus.setText(String.format(mReadBuffer.context.getString(R.string.connected_to_device), msg.obj as String))
                        button.isEnabled = true
                        button.isClickable = true
                    }
                    2-> mBluetoothStatus.setText(R.string.connecting)
                    3-> mBluetoothStatus.setText(R.string.disconnecting)
                    4-> mBluetoothStatus.setText(R.string.disconnected)
                    5-> mBluetoothStatus.setText(R.string.no_address)
                }
            }
        }
    }

    private fun configureReceiver() {
        val filter = IntentFilter()
        filter.addAction(ACTION)
        val receiver = receiver
        receiver.setHandler(MyHandler(findViewById<TextView>(R.id.readBuffer)!!,
            findViewById<TextView>(R.id.bluetooth_status)!!,findViewById<Button>(R.id.button)!!))
        registerReceiver(receiver, filter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            Toast.makeText(this.applicationContext, R.string.no_bluetooth_on_this_device, Toast.LENGTH_LONG).show()
            finishAffinity() // exits the app
        }
        configureReceiver()
        val mText = findViewById<EditText>(R.id.editText)
        findViewById<Button>(R.id.button).setOnClickListener {
            sendNotification(mText.text.toString())
        }
    }

    private fun sendNotification(message:String) {
        Log.d(TAG, "sendNotification $message")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_bluetooth)
            .setContentTitle("My Notification")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                return true
            }
            R.id.action_bluetooth -> {
                popup.showPopup(findViewById<View>(R.id.toolbar))
                return true
            }
            R.id.action_disconnect -> {
                val connectIntent = Intent(this.applicationContext, BLEService::class.java)
                this.applicationContext.startService(connectIntent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this@MainActivity, R.string.bluetooth_enabled, Toast.LENGTH_LONG).show()
                popup.showPopup(findViewById<View>(R.id.toolbar))
            } else {
                Toast.makeText(this@MainActivity, R.string.bluetooth_not_enabled, Toast.LENGTH_LONG).show()
            }
        }
    }
    companion object {
        const val CHANNEL_ID = "Testing notifications ###"

    }
}