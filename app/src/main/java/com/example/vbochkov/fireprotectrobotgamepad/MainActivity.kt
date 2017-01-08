package com.example.vbochkov.fireprotectrobotgamepad

import android.content.pm.ActivityInfo
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class MainActivity() : AppCompatActivity() {

    val firedet_en : ByteArray = "0".toByteArray()
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

    var mtx = ReentrantLock()
    var hasData = mtx.newCondition()
    var commandQueue = LinkedBlockingQueue<ByteArray>()
    var serverThread = ServerThread(commandQueue, mtx, hasData)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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
            val wasEmpty = commandQueue.isEmpty()
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                commandQueue.put(commandMap[target.id])
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                if (target.id != autoGunBtn.id)
                    commandQueue.put(stop)
                else
                    result = false
            } else
                result = false

            mtx.lock()
            if (commandQueue.isNotEmpty() and wasEmpty)
                hasData.signal()
            mtx.unlock()
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

class ServerThread(var commandQueue: BlockingQueue<ByteArray>, var mtx: Lock, var notEmpty: Condition) : Thread() {

    var server = ServerSocket(8989)

    override fun run() {
        val socket = server.accept()

        while (socket.isConnected) {
            mtx.lock()
            try {
                if (commandQueue.isEmpty())
                    notEmpty.await()

                while (commandQueue.isNotEmpty())
                    socket.outputStream.write(commandQueue.take())
            } finally {
                mtx.unlock()
            }
        }
        super.run()
    }
}