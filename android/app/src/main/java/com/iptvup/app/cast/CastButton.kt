package com.iptvup.app.cast

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                runCatching {
                    val button = MediaRouteButton(ctx).apply {
                        contentDescription = "Enviar a Chromecast"
                        CastButtonFactory.setUpMediaRouteButton(ctx, this)
                    }
                    addView(button)
                }
            }
        },
        modifier = modifier
    )
}
