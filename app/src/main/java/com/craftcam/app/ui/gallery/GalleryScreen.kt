package com.craftcam.app.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.craftcam.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val cameraModel: String? = null,
    val isVideo: Boolean = false
)

@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        photos = loadCameleoPhotos(context)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "DARKROOM GALLERY",
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${photos.size} Photos Developed",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VintageAmber)
                }
            } else if (photos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No photographs developed yet.\nTake your first shot with Cameleo!",
                        color = TextMuted,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(photos, key = { _, item -> item.id }) { index, item ->
                        GalleryThumbnail(
                            item = item,
                            onClick = { selectedIndex = index }
                        )
                    }
                }
            }
        }

        // Full Screen Viewer Overlay with Swipe Navigation
        selectedIndex?.let { initialIndex ->
            FullScreenViewer(
                photos = photos,
                initialIndex = initialIndex,
                onDismiss = { selectedIndex = null },
                onDelete = { photoToDelete ->
                    deletePhoto(context, photoToDelete.uri)
                    val newPhotos = photos.filter { it.id != photoToDelete.id }
                    photos = newPhotos
                    if (newPhotos.isEmpty()) {
                        selectedIndex = null
                    }
                },
                onShare = { photoToShare ->
                    sharePhoto(context, photoToShare)
                }
            )
        }
    }
}

@Composable
private fun GalleryThumbnail(
    item: GalleryItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
                if (item.isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bmp = context.contentResolver.loadThumbnail(item.uri, android.util.Size(512, 512), null)
                    bitmap = bmp.asImageBitmap()
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // Load thumbnail downscaled for smooth grid scroll
                        }
                        val bmp = BitmapFactory.decodeStream(stream, null, options)
                        bmp?.let { bitmap = it.asImageBitmap() }
                    }
                }
            } catch (e: Exception) {
                // thumbnail load error fallback
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceElevated)
            .clickable(onClick = onClick)
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TextMuted)
        }

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = VintageAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "REC",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenViewer(
    photos: List<GalleryItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onShare: (GalleryItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val safeInitial = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { photos.size }
    )

    val currentPage = pagerState.currentPage.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val currentItem = photos.getOrNull(currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack.copy(alpha = 0.98f))
    ) {
        // Horizontal Pager for swiping between photos
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> photos.getOrNull(index)?.id ?: index.toLong() }
        ) { page ->
            val item = photos.getOrNull(page)
            if (item != null) {
                FullScreenPhotoPage(item = item)
            }
        }

        // Top Toolbar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = TextPrimary)
            }

            // Photo counter badge e.g. "3 / 12"
            if (photos.isNotEmpty()) {
                Text(
                    text = "${currentPage + 1} / ${photos.size}",
                    color = VintageAmber,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(
                    onClick = {
                        currentItem?.let { onShare(it) }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                }
                IconButton(
                    onClick = {
                        currentItem?.let { onDelete(it) }
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = VintageRed)
                }
            }
        }

        // Left Navigation Arrow (when not on first item)
        if (currentPage > 0) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(currentPage - 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, TextMuted.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Photo",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Right Navigation Arrow (when not on last item)
        if (currentPage < photos.size - 1) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(currentPage + 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, TextMuted.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Photo",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Camera Info Badge
        currentItem?.cameraModel?.let { model ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated.copy(alpha = 0.90f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "SHOT WITH CAMELEO • $model",
                    color = VintageAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FullScreenPhotoPage(item: GalleryItem) {
    val context = LocalContext.current
    var fullBitmap by remember(item.uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isImageLoading by remember(item.uri) { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
                if (item.isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bmp = context.contentResolver.loadThumbnail(item.uri, android.util.Size(1080, 1080), null)
                    fullBitmap = bmp.asImageBitmap()
                } else {
                    // Optimized downscaled decode to fit viewport and prevent OOM
                    context.contentResolver.openInputStream(item.uri)?.use { boundsStream ->
                        val boundsOptions = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
                        val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                        var sample = 1
                        while (maxDim / sample > 2048) {
                            sample *= 2
                        }

                        context.contentResolver.openInputStream(item.uri)?.use { stream ->
                            val decodeOptions = BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                            }
                            val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
                            bmp?.let { fullBitmap = it.asImageBitmap() }
                        }
                    }
                }
            } catch (e: Exception) {
                // error loading image
            } finally {
                isImageLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 70.dp),
        contentAlignment = Alignment.Center
    ) {
        fullBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } ?: if (isImageLoading) {
            CircularProgressIndicator(color = VintageAmber)
        } else {
            Text("Failed to load photo", color = TextMuted, fontSize = 12.sp)
        }

        if (item.isVideo) {
            IconButton(
                onClick = { playVideo(context, item.uri) },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(2.dp, VintageAmber, CircleShape)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = VintageAmber,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

private suspend fun loadCameleoPhotos(context: Context): List<GalleryItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<GalleryItem>()
    val imgProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("CAMELEO_%", "CRAFTCAM_%")
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    // 1. Query Images
    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                var model: String? = null
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    }
                } catch (e: Exception) {
                    // ignore
                }

                items.add(GalleryItem(id, uri, name, date, model, isVideo = false))
            }
        }
    } catch (e: Exception) {
        // ignore
    }

    // 2. Query Videos
    try {
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                items.add(GalleryItem(id, uri, name, date, "Analog Camcorder", isVideo = true))
            }
        }
    } catch (e: Exception) {
        // ignore
    }

    items.sortedByDescending { it.dateAdded }
}

private fun deletePhoto(context: Context, uri: Uri) {
    try {
        context.contentResolver.delete(uri, null, null)
    } catch (e: Exception) {
        // deletion error
    }
}

private fun sharePhoto(context: Context, item: GalleryItem) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isVideo) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, if (item.isVideo) "Share Analog Video" else "Share Vintage Photograph"))
}

private fun playVideo(context: Context, uri: Uri) {
    try {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(playIntent)
    } catch (e: Exception) {
        // fallback
    }
}
