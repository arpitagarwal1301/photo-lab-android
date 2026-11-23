package dev.spikeai.fixtures.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tiny design system for the fixture shop (foundation only, no Material dependency).

internal val Brand = Color(0xFF1F5FBF)

@Composable
internal fun ShopScreen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(title, style = TextStyle(fontSize = 24.sp), modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
internal fun PriceRow(label: String, amount: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(label, style = TextStyle(fontSize = 16.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(amount, style = TextStyle(fontSize = 16.sp))
            trailing?.invoke()
        }
    }
}

/** Icon-only button. [label] = null reproduces the classic "forgot contentDescription" bug. */
@Composable
internal fun IconButton(label: String?) {
    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(32.dp)
            .background(Color(0xFFE0E0E0), CircleShape)
            .clickable { }
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}

@Composable
internal fun PrimaryButton(text: String) {
    BasicText(
        text,
        style = TextStyle(fontSize = 18.sp, color = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand, RoundedCornerShape(8.dp))
            .clickable { }
            .padding(14.dp),
    )
}

@Composable
internal fun Note(text: String) = BasicText(text, style = TextStyle(fontSize = 14.sp, color = Color(0xFF2E7D32)))
