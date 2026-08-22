package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.utils.LocalArtworkUri

internal const val MaxSafeAlbumArtDimensionPx = 2048
internal val SafeOriginalAlbumArtSize = Size(MaxSafeAlbumArtDimensionPx, MaxSafeAlbumArtDimensionPx)

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = SafeOriginalAlbumArtSize,
    placeholderModel: Any? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val requestTargetSize = remember(targetSize) {
        safeAlbumArtTargetSize(targetSize)
    }
    val originalRequestData = remember(uri) {
        if (uri is ImageRequest) uri.data else uri
    }
    val requestData = remember(originalRequestData, requestTargetSize) {
        upgradeRemoteArtworkModel(originalRequestData, requestTargetSize)
    }
    val isStableLocalArtwork = remember(uri) {
        when (uri) {
            is String -> LocalArtworkUri.isLocalArtworkUri(uri)
            is Uri -> LocalArtworkUri.isLocalArtworkUri(uri)
            is ImageRequest -> {
                val data = uri.data
                (data as? String)?.let(LocalArtworkUri::isLocalArtworkUri) == true ||
                    LocalArtworkUri.isLocalArtworkUri(data as? Uri)
            }
            else -> false
        }
    }

    if (renderDirectAlbumArt(
            model = uri,
            title = title,
            modifier = modifier,
            contentScale = contentScale,
        )
    ) {
        return
    }

    val memoryCacheKey = remember(requestData, requestTargetSize) {
        albumArtMemoryCacheKey(requestData, requestTargetSize)
    }
    val placeholderMemoryCacheKey = remember(memoryCacheKey, uri) {
        when (uri) {
            is ImageRequest -> uri.placeholderMemoryCacheKey
                ?: uri.memoryCacheKey
                ?: memoryCacheKey?.let { MemoryCache.Key(it) }
            else -> memoryCacheKey?.let { MemoryCache.Key(it) }
        }
    }
    val requestModel = remember(context, uri, requestData, requestTargetSize) {
        when (uri) {
            is ImageRequest -> uri.newBuilder(context).apply {
                data(requestData)
                size(requestTargetSize)
                if (uri.memoryCacheKey == null) {
                    memoryCacheKey(memoryCacheKey)
                }
                placeholderMemoryCacheKey(placeholderMemoryCacheKey)
            }.build()
            else -> ImageRequest.Builder(context)
                .data(requestData)
                .crossfade(350) // Use Coil's native crossfade
                .error(R.drawable.ic_music_placeholder)
                .size(requestTargetSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(if (isStableLocalArtwork) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .apply {
                    if (memoryCacheKey != null) {
                        memoryCacheKey(memoryCacheKey)
                    }
                    if (placeholderMemoryCacheKey != null) {
                        placeholderMemoryCacheKey(placeholderMemoryCacheKey)
                    }
                }
                .build()
        }
    }
    var lastSuccessPainter by remember(requestModel.data) { mutableStateOf<Painter?>(null) }

    // Use SubcomposeAsyncImage with Coil's native crossfade instead of Crossfade wrapper
    // This avoids recompositions on painter.state changes during scroll.
    SubcomposeAsyncImage(
        model = requestModel,
        contentDescription = "Album art of $title",
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = { state ->
            lastSuccessPainter = state.painter
        },
        loading = { state ->
            val cachedPainter = state.painter ?: lastSuccessPainter
            if (cachedPainter != null) {
                SubcomposeAsyncImageContent(painter = cachedPainter)
            } else if (placeholderModel != null) {
                 SubcomposeAsyncImage(
                    model = placeholderModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    loading = { PlaceholderContent(title = title) },
                    error = { PlaceholderContent(title = title) }
                )
            } else {
                PlaceholderContent(title = title)
            }
        },
        error = {
            val cachedPainter = lastSuccessPainter
            if (cachedPainter != null) {
                SubcomposeAsyncImageContent(painter = cachedPainter)
            } else if (requestData != originalRequestData && originalRequestData != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(originalRequestData)
                        .size(requestTargetSize)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "Album art of $title",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    loading = { PlaceholderContent(title = title) },
                    error = { PlaceholderContent(title = title) },
                )
            } else {
                PlaceholderContent(title = title)
            }
        },
        success = {
            SubcomposeAsyncImageContent()
        }
    )
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_music_placeholder),
            contentDescription = "$title placeholder",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.onSurfaceVariant
            ),
        )
    }
}

@Composable
private fun renderDirectAlbumArt(
    model: Any?,
    title: String,
    modifier: Modifier,
    contentScale: ContentScale,
): Boolean {
    return when (model) {
        is ImageRequest -> renderDirectAlbumArt(model.data, title, modifier, contentScale)
        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = "Album art of $title",
                contentScale = contentScale,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Painter -> {
            Image(
                painter = model,
                contentDescription = "Album art of $title",
                contentScale = contentScale,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is ImageBitmap -> {
            Image(
                bitmap = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Bitmap -> {
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        else -> false
    }
}

internal fun safeAlbumArtTargetSize(targetSize: Size): Size {
    return if (targetSize == Size.ORIGINAL) {
        SafeOriginalAlbumArtSize
    } else {
        targetSize
    }
}

/** Requests the selected resolution from known artwork CDNs instead of upscaling a tiny source. */
internal fun upgradeRemoteArtworkModel(model: Any?, targetSize: Size): Any? {
    val url = model as? String ?: return model
    if (!url.startsWith("https://", ignoreCase = true) &&
        !url.startsWith("http://", ignoreCase = true)
    ) return model

    val dimension = targetSize.maxPixelDimension() ?: return model
    return upgradeRemoteArtworkUrl(url, dimension)
}

internal fun upgradeRemoteArtworkUrl(url: String, requestedDimension: Int): String {
    val dimension = requestedDimension.coerceIn(64, MaxSafeAlbumArtDimensionPx)
    return when {
        url.contains("googleusercontent.com", ignoreCase = true) ||
            url.contains("ggpht.com", ignoreCase = true) -> url
            .replace(Regex("=w\\d+-h\\d+", RegexOption.IGNORE_CASE), "=w$dimension-h$dimension")
            .replace(Regex("=s\\d+", RegexOption.IGNORE_CASE), "=s$dimension")

        url.contains("ytimg.com/vi/", ignoreCase = true) -> url.replace(
            Regex("/(?:default|mqdefault|hqdefault|sddefault|maxresdefault)\\.(?:jpg|webp)", RegexOption.IGNORE_CASE),
            "/${youtubeThumbnailVariant(dimension)}.jpg",
        )

        url.contains("ytimg.com/vi_webp/", ignoreCase = true) -> url.replace(
            Regex("/(?:default|mqdefault|hqdefault|sddefault|maxresdefault)\\.(?:jpg|webp)", RegexOption.IGNORE_CASE),
            "/${youtubeThumbnailVariant(dimension)}.webp",
        )

        url.contains("saavncdn.com", ignoreCase = true) -> url.replace(
            Regex("-\\d+x\\d+(?=\\.(?:jpg|jpeg|png|webp))", RegexOption.IGNORE_CASE),
            "-${dimension}x$dimension",
        )

        url.contains("music.126.net", ignoreCase = true) -> url.replace(
            Regex("([?&]param=)\\d+y\\d+", RegexOption.IGNORE_CASE),
            "\$1${dimension}y$dimension",
        )

        url.contains("y.qq.com", ignoreCase = true) -> url.replace(
            Regex("T002R\\d+x\\d+", RegexOption.IGNORE_CASE),
            "T002R${dimension}x$dimension",
        )

        else -> url
    }
}

private fun youtubeThumbnailVariant(dimension: Int): String = when {
    dimension <= 320 -> "mqdefault"
    dimension <= 512 -> "hqdefault"
    dimension <= 720 -> "sddefault"
    else -> "maxresdefault"
}

private fun Size.maxPixelDimension(): Int? {
    val widthPx = (width as? Dimension.Pixels)?.px
    val heightPx = (height as? Dimension.Pixels)?.px
    return listOfNotNull(widthPx, heightPx).maxOrNull()
}

internal fun albumArtMemoryCacheKey(model: Any?, targetSize: Size): String? {
    val data = when (model) {
        is ImageRequest -> model.data
        else -> model
    } ?: return null

    val baseKey = when (data) {
        is String -> data.takeIf { it.isNotBlank() }
        is Uri -> data.toString().takeIf { it.isNotBlank() }
        else -> null
    } ?: return null

    if (targetSize == Size.ORIGINAL) return baseKey

    val width = (targetSize.width as? Dimension.Pixels)?.px
    val height = (targetSize.height as? Dimension.Pixels)?.px
    return if (width != null && height != null) {
        "${baseKey}_${width}x${height}"
    } else {
        "${baseKey}_${targetSize.width}x${targetSize.height}"
    }
}



//@Composable
//fun OptimizedAlbumArt(
//    uri: String?,
//    title: String,
//    expansionFraction: Float,
//    modifier: Modifier = Modifier,
//    targetSize: Size = Size.ORIGINAL
//) {
//    val context = LocalContext.current
//
//    val painter = rememberAsyncImagePainter(
//        model = ImageRequest.Builder(context)
//            .data(uri)
//            .crossfade(false)
//            .placeholder(R.drawable.ic_music_placeholder)
//            .error(R.drawable.rounded_broken_image_24)
//            .size(targetSize) // Usar el parámetro targetSize
//            .memoryCachePolicy(CachePolicy.ENABLED)
//            .diskCachePolicy(CachePolicy.ENABLED)
//            .build(),
//        onState = { state ->
//            Timber.tag("OptimizedAlbumArt")
//                .d("Painter State (Size: $targetSize): $state for URI: $uri")
//            if (state is AsyncImagePainter.State.Error) {
//                Timber.tag("OptimizedAlbumArt")
//                    .e(state.result.throwable, "Coil Error State for URI: $uri")
//            }
//        }
//    )
//
//    val imageContainerModifier = modifier
//        .padding(vertical = lerp(4.dp, 16.dp, expansionFraction))
//        .fillMaxWidth(lerp(0.5f, 0.8f, expansionFraction))
//        .aspectRatio(1f)
//        //.clip(RoundedCornerShape(lerp(16.dp, 24.dp, expansionFraction)))
//        .graphicsLayer {
//            clip = true
//            alpha = expansionFraction
//        }
//
//    Crossfade(
//        targetState = painter.state,
//        modifier = imageContainerModifier,
//        animationSpec = tween(durationMillis = 350),
//        label = "AlbumArtCrossfade"
//    ) { currentState ->
//        when (currentState) {
//            is AsyncImagePainter.State.Loading,
//            is AsyncImagePainter.State.Empty -> { // Show static placeholder for Loading and Empty states
//                Image(
//                    painter = painterResource(id = R.drawable.ic_music_placeholder),
//                    contentDescription = "$title placeholder", // Adjusted content description
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize(),
//                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
//                )
//            }
//            is AsyncImagePainter.State.Error -> {
//                Timber.tag("OptimizedAlbumArt")
//                    .e(currentState.result.throwable, "Displaying error placeholder for URI: $uri")
//                Image(
//                    painter = painterResource(id = R.drawable.rounded_broken_image_24),
//                    contentDescription = "Error loading album art for $title",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//            is AsyncImagePainter.State.Success -> {
//                Image(
//                    painter = currentState.painter,
//                    contentDescription = "Album art of $title",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//            // Note: AsyncImagePainter.State.Empty is now handled with Loading.
//            // If a distinct visual for Empty is needed and it's different from Loading,
//            // it would need its own branch. For now, grouped with Loading to show the static placeholder.
//        }
//    }
//}
