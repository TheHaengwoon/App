package com.example.driversupervisingsystem

import android.content.ContentValues.TAG
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

private var socket : SocketClient? = null

class DriveStart : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var startTime: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_start)

        val btnDriveStart: Button = findViewById(R.id.btn_drive_start)
        val btnDriveFinish : Button = findViewById(R.id.btn_drive_finish)
        val ivDriverPhoto : ImageView = findViewById(R.id.iv_driver_photo)
        val internalPath = this.filesDir.absolutePath
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val userName = intent.getStringExtra(MemberInformation.Name)
        val userEmail = intent.getStringExtra(MemberInformation.Email)
        //val scope : CoroutineScope = CoroutineScope(Dispatchers.Main)
        var connectionJob: Job? = null

        btnDriveStart.setOnClickListener {
            if (startTime == null) {
                startTime = sdf.format(Date())
                if (userEmail != null) {
                    driveStartTime(userEmail,startTime!!)
                    Toast.makeText(this,"운전 시작: $startTime", Toast.LENGTH_LONG).show()
                }
                connectionJob = lifecycleScope.launch {
                    delay(10000)
                    while(true){
                        try {
                            val currentTime = sdf.format(Date())
                            socket = SocketClient("192.168.32.1",9999,internalPath)
                            val receivedPhoto = socket!!.receivePhoto(currentTime.plus("_").plus(userEmail))
                            withContext(Dispatchers.Main) {
                                if (receivedPhoto != null) {
                                    Toast.makeText(this@DriveStart,"${receivedPhoto.name} downloaded", Toast.LENGTH_LONG).show()
                                    Log.d(TAG,"Image downloaded at $internalPath, and absolutepath is ${receivedPhoto.absolutePath}")
                                    val bitmap = BitmapFactory.decodeFile(receivedPhoto.absolutePath)
                                    ivDriverPhoto.setImageBitmap(bitmap)
                                    ivDriverPhoto.setOnClickListener {
                                        val storageRef = FirebaseStorage.getInstance().reference.child(receivedPhoto.name)
                                        storageRef.putFile(Uri.fromFile(receivedPhoto)).addOnSuccessListener {
                                            // Firestore 에 이미지 URL 저장
                                            if (userEmail != null) {
                                                db.collection("Members").document(userEmail)
                                                    .collection("DrivingSessions").document(startTime!!)
                                                    .collection("DrowsinessEvents").document(receivedPhoto.name.substring(0,15))
                                                    .set(mapOf("url" to receivedPhoto.name))
                                                    .addOnSuccessListener {
                                                        Toast.makeText(
                                                            this@DriveStart,
                                                            "Image URL saved to Firestore.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        Log.d(TAG,"Path : ${db.collection("Members").document(userEmail)
                                                            .collection("DrivingSessions").document(startTime!!)
                                                            .collection("DrowsinessEvents").document(receivedPhoto.name.substring(0,15))}")
                                                    }.addOnFailureListener { exception ->
                                                        Toast.makeText(
                                                            this@DriveStart,
                                                            "Failed to save image URL: ${exception.message}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                            }

                                        }.addOnFailureListener { exception ->
                                            Toast.makeText(this@DriveStart,"Failed to upload image: ${exception.message}",Toast.LENGTH_LONG).show()
                                        }
                                    }

                                } else {
                                    Toast.makeText(this@DriveStart, "사진이 전송되지 않음.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main){
                                Toast.makeText(this@DriveStart,"connection wrong",Toast.LENGTH_LONG).show()
                            }
                        }
                        delay(10000)
                    }
                }
            }else{
                Toast.makeText(this,"현재 운전중입니다",Toast.LENGTH_LONG).show()
            }
        }

        btnDriveFinish.setOnClickListener {
            if (connectionJob?.isActive == true){
                connectionJob?.cancel()
                if (userEmail != null) {
                    if (startTime != null) {
                        val endTime = sdf.format(Date())
                        driveEndTime(userEmail, startTime!!, endTime)
                        Toast.makeText(this,"운전 종료 : $endTime",Toast.LENGTH_LONG).show()
                    }
                }
                connectionJob = null
            }else{
                Toast.makeText(this,"Coroutine is not running",Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun driveStartTime(userEmail : String, time : String){
        db.collection("Members").document(userEmail).collection("DrivingSessions").document(time).set(mapOf("startTime" to time))
    }
    private fun driveEndTime(userEmail : String, startTime : String, endTime : String){
        db.collection("Members").document(userEmail).collection("DrivingSessions").document(startTime).set(mapOf("endTime" to endTime),
            SetOptions.merge())
    }
}
