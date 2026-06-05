package com.example

import android.content.ContentValues
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Locale

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
    var locale by rememberSaveable { mutableStateOf(Locale.getDefault().language.let { if (it == "ro") "ro" else "es" }) }

    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }

    LocalizedContent(locale = locale) {
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(400))
        ) {
            SplashScreen()
        }

        AnimatedVisibility(
            visible = !showSplash,
            enter = fadeIn(animationSpec = tween(400))
        ) {
            GalleryApp(
                currentLocale = locale,
                onLocaleChange = { locale = it }
            )
        }
    }
}

@Composable
fun LocalizedContent(locale: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localizedContext = remember(locale) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(locale))
        context.createConfigurationContext(config)
    }
    val configuration = remember(locale) {
        Configuration(context.resources.configuration).apply { setLocale(Locale(locale)) }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration
    ) {
        content()
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF0F7FF), Color(0xFFDBEAFE))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "👼",
                fontSize = 72.sp,
                modifier = Modifier.scale(scale)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.splash_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp(
    currentLocale: String,
    onLocaleChange: (String) -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val uploadError by viewModel.uploadError.collectAsStateWithLifecycle()

    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, context.getString(R.string.background_uploading), Toast.LENGTH_SHORT).show()
            viewModel.uploadPhotos(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, context.getString(R.string.background_uploading), Toast.LENGTH_SHORT).show()
                viewModel.uploadPhotos(listOf(it))
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingCameraLaunch) {
            pendingCameraLaunch = false
            val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val permission = android.Manifest.permission.CAMERA
        if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            pendingCameraLaunch = true
            cameraPermissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(uploadError) {
        if (uploadError != null) {
            Toast.makeText(context, uploadError, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
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
                    Text(stringResource(R.string.camera))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCameraDialog = false
                    pickerLauncher.launch("image/*")
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gallery))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(R.string.header_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (photos.isNotEmpty()) {
                                Text(
                                    if (photos.size == 1) stringResource(R.string.photo_count_one)
                                    else stringResource(R.string.photo_count, photos.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Language selector
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LanguageChip(
                            text = "ES",
                            selected = currentLocale == "es",
                            onClick = { onLocaleChange("es") }
                        )
                        LanguageChip(
                            text = "RO",
                            selected = currentLocale == "ro",
                            onClick = { onLocaleChange("ro") }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showCameraDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(percent = 50)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.upload_photos))
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            if (photos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.empty_title),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(photos) { index, photo ->
                        PhotoGridItem(
                            url = photo.url,
                            index = index,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedImageIndex = index
                            }
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
fun LanguageChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun PhotoGridItem(url: String, index: Int, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index.toLong() * 50)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.8f)
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = "Photo ${index + 1}",
            contentScale = ContentScale.Crop,
            loading = {
                // Shimmer placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush())
                )
            },
            modifier = Modifier
                .padding(1.5.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClick() }
        )
    }
}

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE8F0FE),
            Color(0xFFD4E4FC),
            Color(0xFFE8F0FE)
        ),
        start = androidx.compose.ui.geometry.Offset(translateAnim - 500f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, 0f)
    )
}

@Composable
fun FullScreenGallery(
    photos: List<com.example.data.Photo>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun downloadPhoto(url: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val imageBytes = URL(url).readBytes()
                    val fileName = "bautizo_liam_${System.currentTimeMillis()}.jpg"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Bautizo Liam")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        uri?.let {
                            context.contentResolver.openOutputStream(it)?.use { os ->
                                os.write(imageBytes)
                            }
                        }
                    } else {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Bautizo Liam")
                        dir.mkdirs()
                        File(dir, fileName).writeBytes(imageBytes)
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, context.getString(R.string.download_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.download_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        // Top bar with back and counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            // Download button
            IconButton(onClick = {
                downloadPhoto(photos[pagerState.currentPage].url)
            }) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download), tint = Color.White)
            }
        }
    }
}
