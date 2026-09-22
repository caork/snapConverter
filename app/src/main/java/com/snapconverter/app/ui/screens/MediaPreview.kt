package com.snapconverter.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snapconverter.engine.policy.MediaKind
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val ThumbShape = RoundedCornerShape(10.dp)
private const val MAX_THUMB_W = 140
private const val MAX_THUMB_H = 92

@Composable
fun AspectMediaThumb(
    uri: Uri,
    kind: MediaKind?,
    displayWidth: Int,
    displayHeight: Int,
    onClick: () -> Unit,
) {
    val w = displayWidth.coerceAtLeast(1)
    val h = displayHeight.coerceAtLeast(1)
    val aspect = w.toFloat() / h.toFloat()
    val landscape = aspect >= 1f
    val thumbModifier = if (landscape) {
        Modifier
            .width(MAX_THUMB_W.dp)
            .aspectRatio(aspect)
    } else {
        Modifier
            .height(MAX_THUMB_H.dp)
            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
    }
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, w, h, kind) {
        value = withContext(Dispatchers.IO) {
            loadThumb(context, uri, kind, w, h)
        }
    }
    Box(
        modifier = thumbModifier
            .clip(ThumbShape)
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "预览",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (kind == MediaKind.VIDEO) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun MediaPreviewDialog(
    uri: Uri,
    kind: MediaKind?,
    displayWidth: Int,
    displayHeight: Int,
    onDismiss: () -> Unit,
) {
    val aspect = run {
        val w = displayWidth.coerceAtLeast(1).toFloat()
        val h = displayHeight.coerceAtLeast(1).toFloat()
        (w / h).coerceIn(0.4f, 2.5f)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box {
                    if (kind == MediaKind.IMAGE) {
                        ImagePreview(uri, aspect)
                    } else {
                        VideoPreview(uri, aspect)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(uri: Uri, aspect: Float) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            loadPreviewImage(context, uri)
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Photo, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun VideoPreview(uri: Uri, aspect: Float) {
    val context = LocalContext.current
    val playback = remember(uri) { PreviewPlayback(context, uri) }
    DisposableEffect(playback) {
        onDispose { playback.release() }
    }
    LaunchedEffect(playback.playing, playback.ready) {
        while (isActive && playback.playing && playback.ready) {
            playback.poll()
            delay(200)
        }
    }
    var scrub by remember { mutableFloatStateOf(-1f) }
    val duration = playback.durationMs.coerceAtLeast(0)
    val shown = if (scrub >= 0f) scrub else playback.positionMs.toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { playback.toggle() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                playback.attachSurface(st) { vw, vh ->
                                    applyTransform(this@apply, vw, vh)
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                applyTransform(this@apply, playback.videoWidth, playback.videoHeight)
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                playback.detachSurface()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.matchParentSize(),
                onRelease = { playback.detachSurface() },
            )
            if (playback.ready && !playback.playing) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE6000000))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { playback.toggle() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playback.playing) "暂停" else "播放",
                    tint = Color.White,
                )
            }
            Text(formatPlayerTime(shown.toInt()), color = Color.White, fontSize = 11.sp)
            Slider(
                value = shown.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = {
                    scrub = it
                    playback.seek(it.toInt())
                },
                onValueChangeFinished = { scrub = -1f },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = playback.ready && duration > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                    disabledThumbColor = Color.White.copy(alpha = 0.5f),
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.4f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            )
            Text(formatPlayerTime(duration), color = Color.White, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
        }
    }
}

private fun formatPlayerTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private class PreviewPlayback(
    context: android.content.Context,
    private val uri: Uri,
) {
    private val app = context.applicationContext
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var sizeListener: ((Int, Int) -> Unit)? = null
    var durationMs by mutableIntStateOf(0)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set

    fun attachSurface(st: SurfaceTexture, onSize: (Int, Int) -> Unit) {
        sizeListener = onSize
        val next = Surface(st)
        surface?.release()
        surface = next
        val existing = player
        if (existing != null) {
            existing.setSurface(next)
            if (videoWidth > 0 && videoHeight > 0) onSize(videoWidth, videoHeight)
            return
        }
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setSurface(next)
            mp.setDataSource(app, uri)
            mp.isLooping = false
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoWidth = w
                videoHeight = h
                sizeListener?.invoke(w, h)
            }
            mp.setOnPreparedListener { prepared ->
                durationMs = prepared.duration.coerceAtLeast(0)
                videoWidth = prepared.videoWidth
                videoHeight = prepared.videoHeight
                sizeListener?.invoke(videoWidth, videoHeight)
                ready = true
                prepared.start()
                playing = true
            }
            mp.setOnCompletionListener {
                playing = false
                positionMs = durationMs
            }
            mp.prepareAsync()
        }.onFailure {
            runCatching { mp.release() }
            player = null
        }
    }

    fun detachSurface() {
        player?.setSurface(null)
        surface?.release()
        surface = null
    }

    fun toggle() {
        val p = player ?: return
        if (!ready) return
        if (p.isPlaying) {
            p.pause()
            playing = false
            positionMs = p.currentPosition
        } else {
            if (durationMs > 0 && positionMs >= durationMs - 200) {
                p.seekTo(0)
                positionMs = 0
            }
            p.start()
            playing = true
        }
    }

    fun seek(ms: Int) {
        val p = player ?: return
        if (!ready) return
        val target = ms.coerceIn(0, durationMs.coerceAtLeast(0))
        p.seekTo(target)
        positionMs = target
    }

    fun poll() {
        val p = player ?: return
        if (p.isPlaying) positionMs = p.currentPosition
    }

    fun release() {
        playing = false
        ready = false
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        surface?.release()
        surface = null
        sizeListener = null
    }
}

private fun applyTransform(view: TextureView, videoW: Int, videoH: Int) {
    if (videoW <= 0 || videoH <= 0 || view.width <= 0 || view.height <= 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = minOf(viewW / videoW, viewH / videoH)
    val matrix = android.graphics.Matrix()
    matrix.setScale(
        (videoW * scale) / viewW,
        (videoH * scale) / viewH,
        viewW / 2f,
        viewH / 2f,
    )
    view.setTransform(matrix)
}

private fun loadThumb(
    context: android.content.Context,
    uri: Uri,
    kind: MediaKind?,
    displayW: Int,
    displayH: Int,
): Bitmap? {
    val (tw, th) = fitLongEdge(displayW, displayH, 480)
    return if (kind == MediaKind.IMAGE) {
        decodeImage(context, uri, tw, th)
    } else {
        frameAt(context, uri, tw, th) ?: decodeImage(context, uri, tw, th)
    }
}

private fun loadPreviewImage(context: android.content.Context, uri: Uri): Bitmap? =
    decodeImage(context, uri, 1080, 1080)

private fun frameAt(context: android.content.Context, uri: Uri, w: Int, h: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getScaledFrameAtTime(
            0L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            w.coerceAtLeast(2),
            h.coerceAtLeast(2),
        )
    } catch (_: Throwable) {
        null
    } finally {
        retriever.release()
    }
}

private fun decodeImage(context: android.content.Context, uri: Uri, maxW: Int, maxH: Int): Bitmap? {
    return runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val srcW = info.size.width.coerceAtLeast(1)
            val srcH = info.size.height.coerceAtLeast(1)
            val (tw, th) = fitInside(srcW, srcH, maxW, maxH)
            decoder.setTargetSize(tw, th)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}

private fun fitLongEdge(width: Int, height: Int, maxLong: Int): Pair<Int, Int> {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val long = maxOf(w, h)
    if (long <= maxLong) return w to h
    val scale = maxLong.toFloat() / long
    return (w * scale).roundToInt().coerceAtLeast(2) to (h * scale).roundToInt().coerceAtLeast(2)
}

private fun fitInside(width: Int, height: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
    val scale = minOf(maxW.toFloat() / width, maxH.toFloat() / height, 1f)
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}
