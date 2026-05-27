package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.Photo
import com.example.network.CloudinaryApi
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import androidx.core.graphics.scale
import androidx.core.net.toUri

class UploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var cloudinaryApi: CloudinaryApi

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStrings = inputData.getStringArray("URIS") ?: return@withContext Result.failure()
        
        if (uriStrings.isEmpty()) return@withContext Result.success()

        initFirebase(appContext)
        initCloudinary()

        createNotificationChannel()

        var uploadedCount = 0
        uriStrings.forEachIndexed { index, uriString ->
            setForeground(createForegroundInfo(index, uriStrings.size))
            try {
                val uri = uriString.toUri()
                val file = compressImage(uri, appContext)
                if (file != null) {
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    
                    val preset = "liamBautizo".toRequestBody("text/plain".toMediaTypeOrNull())
                    val folder = "bautizo".toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val response = cloudinaryApi.uploadImage(preset, folder, body)
                    
                    val photoDoc = Photo(
                        url = response.secure_url,
                        publicId = response.public_id,
                        createdAt = Timestamp(Date())
                    )
                    
                    firestore.collection("photos").add(photoDoc)
                    uploadedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext if (uploadedCount > 0) {
            val output = androidx.work.workDataOf("count" to uploadedCount, "time" to System.currentTimeMillis())
            Result.success(output)
        } else {
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val id = "upload_channel"
        val title = appContext.getString(R.string.uploading)
        val content = "${progress + 1} / $total"

        val notification = NotificationCompat.Builder(appContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setProgress(total, progress, false)
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             ForegroundInfo(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
             ForegroundInfo(1, notification)
        }
    }

    private fun createNotificationChannel() {
        val name = "Uploads"
        val descriptionText = "Image Uploads"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel("upload_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun initFirebase(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId("liambautizo-d4d42")
                .setApplicationId("1:263988039159:web:1f8051be13be20bfec32f2")
                .setApiKey("AIzaSyD8_H6VuNDpW-F-APQOwLGVCltjaPVL5OA")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        firestore = FirebaseFirestore.getInstance()
    }

    private fun initCloudinary() {
        if (!::cloudinaryApi.isInitialized) {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.cloudinary.com/v1_1/dzirz4hyk/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            cloudinaryApi = retrofit.create(CloudinaryApi::class.java)
        }
    }

    private fun compressImage(uri: Uri, context: Context): File? {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return null

            val maxPixel = 1200f
            val ratio = (maxPixel / bitmap.width).coerceAtMost(maxPixel / bitmap.height)
            val finalWidth = if (ratio < 1) (bitmap.width * ratio).toInt() else bitmap.width
            val finalHeight = if (ratio < 1) (bitmap.height * ratio).toInt() else bitmap.height

            val resizedBitmap = bitmap.scale(finalWidth, finalHeight)

            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val bitmapData = outputStream.toByteArray()

            val tempFile = File(context.cacheDir, "upload_work_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(tempFile)
            fos.write(bitmapData)
            fos.flush()
            fos.close()
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
