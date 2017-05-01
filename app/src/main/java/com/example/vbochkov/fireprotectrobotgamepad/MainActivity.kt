package com.example.vbochkov.fireprotectrobotgamepad

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


val piConnected : Int = 1
val piDisconnected : Int = 2

class MainActivity() : AppCompatActivity() {

    var autogun_enabled : Boolean = false
    val firedet_en : ByteArray = "a".toByteArray()
    val robot_forw : ByteArray = "1".toByteArray()
    val robot_back : ByteArray = "2".toByteArray()
    val robot_right : ByteArray = "3".toByteArray()
    val robot_left : ByteArray = "4".toByteArray()
    val gun_up : ByteArray = "5".toByteArray()
    val gun_down : ByteArray = "6".toByteArray()
    val gun_left : ByteArray = "7".toByteArray()
    val gun_right : ByteArray = "8".toByteArray()
    val pump_on : ByteArray = "p".toByteArray()
    val stop : ByteArray = "s".toByteArray()
    val power_off : ByteArray = "e".toByteArray()

    var servMtx = ReentrantLock()
    var hasData = servMtx.newCondition()
    var socketCommandsQueue = LinkedBlockingQueue<ByteArray>()

    val toastMap = mapOf(
        piConnected to "Raspberry PI is connected!",
        piDisconnected to "Raspberry PI is disconnected!"
    )

    fun showToast(toastId: Int): Boolean {
        Toast.makeText(this, toastMap[toastId], Toast.LENGTH_SHORT).show()
        return true
    }

    val showToastHandler = Handler { message ->
        when (message.what) {
            piConnected -> showToast(piConnected)
            piDisconnected -> showToast(piDisconnected)
            else -> false
        }
    }

    var serverThread = ServerThread(
        socketCommandsQueue, servMtx, hasData, showToastHandler
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = resources.configuration.orientation

        val commandMap = mapOf(
            rForwBtn.id to robot_forw,
            rBackBtn.id to robot_back,
            rLeftBtn.id to robot_left,
            rRightBtn.id to robot_right,
            gUpBtn.id to gun_up,
            gDownBtn.id to gun_down,
            gLeftBtn.id to gun_left,
            gRightBtn.id to gun_right,
            pumpBtn.id to pump_on,
            autoGunBtn.id to firedet_en
        )

        fun btnOnTouch(target: View, motionEvent: MotionEvent): Boolean {
            var result = true
            val wasEmpty = socketCommandsQueue.isEmpty()
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                socketCommandsQueue.put(commandMap[target.id])
                if (target.id == autoGunBtn.id) {
                    autogun_enabled = !autogun_enabled
                    if (autogun_enabled) {
                        autoGunBtn.setText("STOP AUTO GUN")
                    } else {
                        autoGunBtn.setText("START AUTO GUN")
                    }
                    rForwBtn.setEnabled(!autogun_enabled)
                    rBackBtn.setEnabled(!autogun_enabled)
                    rLeftBtn.setEnabled(!autogun_enabled)
                    rRightBtn.setEnabled(!autogun_enabled)
                    gUpBtn.setEnabled(!autogun_enabled)
                    gDownBtn.setEnabled(!autogun_enabled)
                    gLeftBtn.setEnabled(!autogun_enabled)
                    gRightBtn.setEnabled(!autogun_enabled)
                    pumpBtn.setEnabled(!autogun_enabled)
                    autogun_enabled = !autogun_enabled
                }
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                if (target.id != autoGunBtn.id)
                    socketCommandsQueue.put(stop)
                else
                    result = false
            } else
                result = false

            servMtx.lock()
            if (socketCommandsQueue.isNotEmpty() and wasEmpty)
                hasData.signal()
            servMtx.unlock()
            return result
        }

        rForwBtn.setOnTouchListener(::btnOnTouch)
        rBackBtn.setOnTouchListener(::btnOnTouch)
        rLeftBtn.setOnTouchListener(::btnOnTouch)
        rRightBtn.setOnTouchListener(::btnOnTouch)
        rLeftBtn.setOnTouchListener(::btnOnTouch)
        pumpBtn.setOnTouchListener(::btnOnTouch)
        autoGunBtn.setOnTouchListener(::btnOnTouch)
        gUpBtn.setOnTouchListener(::btnOnTouch)
        gDownBtn.setOnTouchListener(::btnOnTouch)
        gLeftBtn.setOnTouchListener(::btnOnTouch)
        gRightBtn.setOnTouchListener(::btnOnTouch)
        serverThread.start()
    }
}

class ServerThread(
        var sockCommandsQueue: BlockingQueue<ByteArray>,
        var servMtx: Lock,
        var commNotEmpty: Condition,
        var showToast: Handler)
    : Thread() {

    var server = ServerSocket(8989)

    override fun run() {
        val socket = server.accept()
        var mess = showToast.obtainMessage(piConnected)
        mess.sendToTarget()

        while (socket.isConnected) {
            servMtx.lock()
            try {
                if (sockCommandsQueue.isEmpty())
                    commNotEmpty.await()
                while (sockCommandsQueue.isNotEmpty())
                    socket.outputStream.write(sockCommandsQueue.take())
            } finally {
                servMtx.unlock()
            }
        }

        mess = showToast.obtainMessage(piDisconnected)
        mess.sendToTarget()
        super.run()
    }
}