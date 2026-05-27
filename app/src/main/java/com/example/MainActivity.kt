package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GalleryViewModel
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var showSplash by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
    } else {
        GalleryApp()
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "👼", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Bautizo de Liam", 
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel = viewModel()) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val uploadError by viewModel.uploadError.collectAsStateWithLifecycle()

    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var showCameraDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.background_uploading), Toast.LENGTH_SHORT).show()
            viewModel.uploadPhotos(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { 
                Toast.makeText(context, context.getString(R.string.background_uploading), Toast.LENGTH_SHORT).show()
                viewModel.uploadPhotos(listOf(it)) 
            }
        }
    }

    val uploadedPhotoMsg = stringResource(R.string.photo_uploaded)
    val uploadedMultipleMsg = stringResource(R.string.photo_uploaded_multiple)

    LaunchedEffect(Unit) {
        val workManager = WorkManager.getInstance(context)
        val liveData = workManager.getWorkInfosLiveData(androidx.work.WorkQuery.Builder.fromStates(listOf(WorkInfo.State.SUCCEEDED)).build())
        val observer = androidx.lifecycle.Observer<List<WorkInfo>> { infos ->
            val recentInfos = infos.filter { System.currentTimeMillis() - it.outputData.getLong("time", 0) < 2000 }
            for (info in recentInfos) {
                // To avoid multiple toasts from the same worker if observe triggers multiple times,
                // we set time to 0 after showing. WorkManager outputData is immutable, so we can't.
                // We'll rely on the 2s window which is acceptable for a simple toast.
                val count = info.outputData.getInt("count", 0)
                if (count == 1) {
                    Toast.makeText(context, uploadedPhotoMsg, Toast.LENGTH_SHORT).show()
                } else if (count > 1) {
                    Toast.makeText(context, uploadedMultipleMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        liveData.observeForever(observer)
    }

    fun launchCamera() {
        val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text(stringResource(R.string.upload_photos)) },
            text = { Text(stringResource(R.string.share_subtitle)) },
            confirmButton = {
                TextButton(onClick = {
                    showCameraDialog = false
                    launchCamera()
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cámara")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCameraDialog = false
                    pickerLauncher.launch("image/*")
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Galería")
                }
            }
        )
    }
    
    LaunchedEffect(uploadError) {
        if (uploadError != null) {
            Toast.makeText(context, uploadError, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.header_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCameraDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(percent = 50)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.upload_photos))
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (photos.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.empty_title),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(1.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(photos) { index, photo ->
                        AsyncImage(
                            model = photo.url,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(1.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedImageIndex = index }
                        )
                    }
                }
            }
        }
    }

    if (selectedImageIndex != null) {
        FullScreenGallery(
            photos = photos,
            initialIndex = selectedImageIndex!!,
            onDismiss = { selectedImageIndex = null }
        )
    }
}

@Composable
fun FullScreenGallery(
    photos: List<com.example.data.Photo>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = photos[page].url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}
