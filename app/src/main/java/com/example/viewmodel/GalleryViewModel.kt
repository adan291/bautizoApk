package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.data.Photo
import com.example.worker.UploadWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    private lateinit var firestore: FirebaseFirestore

    init {
        initFirebase(application)
        listenToPhotos()
    }

    private fun initFirebase(context: Application) {
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

    private fun listenToPhotos() {
        firestore.collection("photos")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uploadError.value = error.message
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val newPhotos = snapshot.documents.mapNotNull { it.toObject(Photo::class.java) }
                    _photos.value = newPhotos
                }
            }
    }

    fun uploadPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val tempFiles = mutableListOf<String>()
                
                // Copy to cache to avoid permission loss in Worker
                for (uri in uris) {
                    val cacheFile = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    inputStream?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                        tempFiles.add(Uri.fromFile(cacheFile).toString())
                    }
                }
                
                if (tempFiles.isNotEmpty()) {
                    val workData = workDataOf("URIS" to tempFiles.toTypedArray())
                    val request = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(workData)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                        
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "upload_${System.currentTimeMillis()}",
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        request
                    )
                }
            } catch (e: Exception) {
                _uploadError.value = "Error scheduling upload: ${e.message}"
            }
        }
    }

    fun clearError() {
        _uploadError.value = null
    }
}
