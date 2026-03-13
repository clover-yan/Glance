package top.khyan.glance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text

private val MaterialSymbolsOutlined = FontFamily(
    Font(R.font.material_symbols_outlined)
)

@Composable
fun MaterialSymbol(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val iconSize = with(LocalDensity.current) { size.toSp() }
    val semanticsModifier = Modifier.clearAndSetSemantics {
        if (contentDescription != null) {
            this.contentDescription = contentDescription
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = tint,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = MaterialSymbolsOutlined,
                fontSize = iconSize,
                lineHeight = iconSize,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

