package com.example.data

import com.google.firebase.Timestamp

data class Photo(
    val url: String = "",
    val publicId: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
