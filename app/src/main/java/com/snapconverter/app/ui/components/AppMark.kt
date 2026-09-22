package com.snapconverter.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snapconverter.app.R
import com.snapconverter.app.ui.theme.ios27.Ios27Brand

/**
 * The app's own mark, drawn from the launcher icon's foreground vector so the
 * header and the home screen can never drift apart. The foreground occupies
 * 61 of its 108dp viewport, so the image is scaled by 108/61 × 0.72 to leave
 * the same optical margin an icon mask would.
 */
@Composable
fun AppMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.verticalGradient(
                    listOf(Ios27Brand.markTop, Ios27Brand.markBottom),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_fg),
            contentDescription = null,
            modifier = Modifier.size(size * 1.27f),
        )
    }
}
