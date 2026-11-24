package com.craftcam.app.ui.gallery

import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle

@Composable
internal fun GalleryVideoPage(item: GalleryItem) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    var playing by remember(item.uri) { mutableStateOf(true) }
    var position by remember(item.uri) { mutableIntStateOf(0) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    val loop = item.cameraModel.equals("D3D", ignoreCase = true) ||
        item.name.startsWith("CAMELEO_3D_", ignoreCase = true)

    Box(Modifier.fillMaxSize().padding(vertical = 70.dp), contentAlignment = Alignment.Center) {
        if (failed) {
            TextButton(onClick = { failed = false; playing = true; position = 0 }) {
                Text("Video couldn’t play. Retry")
            }
        } else if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            var ready by remember { mutableStateOf(false) }
            var ratio by remember { mutableFloatStateOf(1f) }
            AndroidView(
                modifier = Modifier.aspectRatio(ratio),
                factory = { context ->
                    VideoView(context).apply {
                        setOnPreparedListener { player ->
                            player.isLooping = loop
                            if (player.videoWidth > 0 && player.videoHeight > 0) {
                                ratio = player.videoWidth.toFloat() / player.videoHeight
                            }
                            if (position > 0) seekTo(position)
                            ready = true
                        }
                        setOnCompletionListener { playing = false; position = 0 }
                        setOnErrorListener { _, _, _ -> failed = true; true }
                        setVideoURI(item.uri)
                    }
                },
                onRelease = { view ->
                    if (ready) position = view.currentPosition.coerceAtLeast(0)
                    view.stopPlayback()
                },
                update = { view ->
                    if (ready) {
                        if (playing) view.start() else view.pause()
                    }
                }
            )
            if (ready) {
                Box(
                    Modifier.matchParentSize().clickable(
                        role = Role.Button,
                        onClickLabel = if (playing) "Pause video" else "Play video"
                    ) { playing = !playing },
                    contentAlignment = Alignment.Center
                ) {
                    if (!playing) Icon(Icons.Default.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
