package com.madurasoftware.vmble

import android.app.Activity
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Handler
import android.util.Log
import android.widget.ArrayAdapter
import java.util.*
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat





const val REQUEST_ENABLE_BT = 100
const val MESSAGE_READ = 2 // used in bluetooth handler to identify message update

const val MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 15

private const val TAG = "BluetoothUtils"
private val mHandler = Handler()
const val SCAN_PERIOD:Long = 1000
internal var devicesDiscovered = TreeSet<DeviceWrapper>()
private var mLastDeviceConnected: DeviceWrapper = DeviceWrapper(DeviceWrapper.DeviceType.NONE,"","")

val btScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner!!

fun isBluetoothSupported(): Boolean {
    if (BluetoothAdapter.getDefaultAdapter() == null) {
        return false
    }
    return true
}

private fun startBluetooth(activity: Activity): Boolean {
    if (!isBluetoothSupported()) {
        activity.finishAffinity()
        return false
    }
    if (!BluetoothAdapter.getDefaultAdapter()!!.isEnabled) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        return false
    } else {
        Log.d(ContentValues.TAG,"Bluetooth already enabled")
    }
    return true
}

fun listPairedDevices(activity: Activity, array: ArrayAdapter<DeviceWrapper>):Boolean {
    if (!isBluetoothSupported()) {
        return false
    }
    try {
        if (!startBluetooth(activity)) {
            return false
        }
    } catch (e: Exception) {
        Log.d(ContentValues.TAG, "exception $e")
        return false
    }
//    val permissionCheck = ContextCompat.checkSelfPermission(activity.baseContext, android.Manifest.permission.ACCESS_FINE_LOCATION)
//    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
        )
//    }
    startScanning(array)
//    val pairedDevices = BluetoothAdapter.getDefaultAdapter()!!.getBondedDevices()
//    for (device in pairedDevices!!) {
//        array.add(DeviceWrapper(DeviceWrapper.DeviceType.V4,device.name,device.address))
//    }
    return true
}

fun requestBlePermissions(activity: Activity, requestCode: Int) {
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        requestCode
    )
}

fun connectToAddress(context: Context, d:DeviceWrapper) {
    mLastDeviceConnected = d
    if (d.deviceType == DeviceWrapper.DeviceType.BLE) {
        Log.d(TAG, "Invoking BLEService with ${d.address}")
        val connectIntent = Intent(context, BLEService::class.java)
        connectIntent.putExtra(BLEService.CONNECTION,d.address)
        context.startService(connectIntent)
    } else {
        // only BLE is supported
    }
}

// Device scan callback.
private val leScanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val d = DeviceWrapper(DeviceWrapper.DeviceType.BLE,result.device.name,result.device.address)
//        Log.d(TAG, "discovered ${d}")
        if (devicesDiscovered.add(d)) {
            Log.d(TAG, "added $d")
        }
    }
}
private fun startScanning(array: ArrayAdapter<DeviceWrapper>) {
    Log.d(TAG, "starting BLE scan")
    devicesDiscovered.clear()
    AsyncTask.execute { btScanner.startScan(leScanCallback) }
    mHandler.postDelayed({ stopScanning(array) }, SCAN_PERIOD)
}

fun stopScanning(array: ArrayAdapter<DeviceWrapper>) {
    Log.d(TAG,"stopping BLE scan")
    AsyncTask.execute { btScanner.stopScan(leScanCallback) }
    array.addAll(devicesDiscovered)
    array.notifyDataSetChanged()
}