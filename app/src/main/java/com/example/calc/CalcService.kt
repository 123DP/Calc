package com.example.calc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import kotlinx.coroutines.*
import java.io.*
import java.lang.NumberFormatException
import java.net.*
import javax.net.ssl.HttpsURLConnection

class CalcService: Service() {

    private lateinit var output: PrintWriter
    private lateinit var input: BufferedReader
//    private lateinit var socket: Socket
    private var running = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForeground()

        if (intent?.action == "stop") {
            running = false
            stopSelf()
        }

        Log.i("Calc", "onStartCommand Running: $running")
        if (intent?.action == "cloud") {
            vibratePhone(false)
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    sendStoredDataToBackend()
                }
            }
        }

        if (intent?.action == "pi") {
            running = true
            val prefs: SharedPreferences = getSharedPreferences("fileDataTest", Context.MODE_PRIVATE)
            val serverIp = prefs.getString("ip","0.0.0.0")!!
            val port = prefs.getInt("port",5000)
            GlobalScope.launch {
                beginPiServerConnection(serverIp, port)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun sendStoredDataToBackend() {
        for (i in 0 until getStoredDataCount()) {
            var data = loadDataFromStorage(i)
            if (data != null) {
                data = data.replace("\"{","{")
                data = data.replace("}\"","}")
                data = data.replace("\\\\\"", "\"")
                sendDataToBackend(data)
                Log.i("Calc", "data: $data")
            }
        }
        vibratePhone(false)
    }

    private fun loadDataFromStorage(i: Int): String? {

        val prefs = getSharedPreferences("fileDataTest", Context.MODE_PRIVATE)
        return prefs.getString("File$i", "")
    }

    private fun vibratePhone(vibrateLengthLong: Boolean) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (vibrator.hasVibrator()) { // Vibrator availability checking
            val longArray = longArrayOf(0,500,750,1250, 1500, 1750)
            val vibrationLong = VibrationEffect.createWaveform(longArray, -1)
            val vibrationShort = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(if (vibrateLengthLong) vibrationLong else vibrationShort)
        }
    }

    private fun connectToPiServer(server_ip: String, port: Int): Boolean {

        Log.i("Calc", "Connecting to $server_ip port: $port")

        try {
            val socket = Socket(server_ip, port)
            val outputStream = socket.getOutputStream()
            output = PrintWriter(outputStream)
            val inputStream = socket.getInputStream()
            val inputStreamReader = InputStreamReader(inputStream)
            input = BufferedReader(inputStreamReader)
            Log.i("CalcService", "Connected")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.i("CalcService", "Connection Failure")
            return false
            // Failed
        }
        return true
    }

    private suspend fun beginPiServerConnection(server_ip: String, port: Int) {

        withContext(Dispatchers.IO) {
            Log.i("Calc", "IO: ")
            var attempts = 0
            while (true) {
                Log.i("Calc", "In Loop")
                if (connectToPiServer(server_ip, port)) {
                    communicateWithPiServer()
                    break
                }
                delay(5000)
                attempts++
                Log.i("CalcService", "Retry Attempts: $attempts")
                if (!running) break
            }
        }
    }

    private fun communicateWithPiServer() : Boolean {
        tx("go")
        val rx = rx()
        if (rx!="Ack") return false
        vibratePhone(false)
        tx("Files")
        val numberOfFilesAsString = rx()
        val numberOfFiles: Int
        try {
            numberOfFiles = numberOfFilesAsString?.toInt()!!
        } catch (e: NumberFormatException) {
            return false
        }
        storeDataCount(numberOfFiles)
        requestDataFromPi(numberOfFiles)
        tx("Bye")
        vibratePhone(false)
        stopSelf()
        return true
    }

    private fun getStoredDataCount() : Int {
        val prefs = getSharedPreferences("fileDataTest", Context.MODE_PRIVATE)
        return prefs.getInt("Number",0)
    }

    private fun storeDataCount(numberOfFiles: Int) {

        val prefs: SharedPreferences = getSharedPreferences("fileDataTest", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("Number", numberOfFiles)
        editor.commit()

    }

    private fun requestDataFromPi(numberOfFiles: Int) {

        for (currentPos in 0 until numberOfFiles) {
            tx("$currentPos")
            if (rx()=="data") {
                val success = receiveFile(input, currentPos)
                if (!success) {
                    Toast.makeText(this, "File: $currentPos Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tx(data: String) {
        Log.i("Calc", "Sending Data: $data")
        output.print(data)
        output.flush()
    }

    private fun rx() : String? {
        while (true) {
            return try {
                val receivedLine = input.readLine()
                Log.i("Calc", "Received Data: $receivedLine")
                receivedLine
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun receiveFile(input: BufferedReader, filePos: Int) : Boolean {
        Log.i("Calc", "Data")
        var receivedData = ""
        var dataFrames = 0
        while (true) {
            try {
                val line = input.readLine()
                dataFrames++
                //            input.read();
                Log.i("Calc", "F: $dataFrames $line")
                receivedData += line
                if (line.contains("'end'")) {
                    Log.i("Calc", "end received")
                    break
                }
            } catch (e: IOException) {
                return false
            }
        }
        receivedData = receivedData.replace("b'", "")
        receivedData = receivedData.replace("'end'", "")
        receivedData = receivedData.replace("'", "")
        receivedData = receivedData.replace("\n", "")
        val length = receivedData.length
        Log.i("Calc", "Frames Received: $dataFrames")
        Log.i("Calc", "length: $length")
        // 214588
        Log.i("Calc", receivedData)
        saveDataToStroage(receivedData, filePos)
        return true
    }

    private fun saveDataToStroage(data: String, currentFilePos: Int) {

        val prefs: SharedPreferences = getSharedPreferences("fileDataTest", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("File$currentFilePos", data)
        editor.commit()
    }

    private suspend fun sendDataToBackend(data: String) {
        withContext(Dispatchers.IO) {

            val url = URL("https://saitproject.azurewebsites.net/d")
            with(url.openConnection() as HttpsURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type","application/json")
                val wr = OutputStreamWriter(outputStream)
                wr.write(data)
                wr.flush()

                println("URL: $url")
                println("Response Code: $responseCode")

                BufferedReader(InputStreamReader(inputStream)).use {
                    val response = StringBuffer()
                    var inputLine = it.readLine()
                    while (inputLine != null) {
                        response.append(inputLine)
                        inputLine = it.readLine()
                    }
                    println("Response : $response")
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun startForeground() {

        val channelId =
            createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(this, channelId )
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(101, notification)
    }

    private fun createNotificationChannel(): String{
        val channelId = "my_service"
        val channelName = "My Background Service"
        val channel = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH)
        channel.lightColor = Color.BLUE
        channel.importance = NotificationManager.IMPORTANCE_NONE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }
}