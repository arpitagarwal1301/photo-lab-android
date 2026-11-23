package dev.spikeai.spike.paparazzi

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First QA fixture. Two seeded bugs the agent should eventually find:
 *  1. The remove-item control has no label (icon-only, no contentDescription).
 *  2. The pay button total (₹1,499) does not match the order total (₹1,498).
 */
@Composable
fun CheckoutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            "Checkout",
            style = TextStyle(fontSize = 24.sp),
            modifier = Modifier.semantics { heading() },
        )
        CartRow(name = "Blue light glasses", price = "₹999")
        CartRow(name = "Lens cleaning kit", price = "₹499")
        BasicText("Order total: ₹1,498", style = TextStyle(fontSize = 18.sp))
        BasicText(
            "Pay ₹1,499", // seeded bug 2
            style = TextStyle(fontSize = 18.sp, color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1F5FBF), RoundedCornerShape(8.dp))
                .clickable { }
                .padding(14.dp),
        )
        BasicText(
            "Apply coupon",
            style = TextStyle(fontSize = 16.sp, color = Color(0xFF1F5FBF)),
            modifier = Modifier
                .clickable { }
                .semantics { contentDescription = "Apply coupon code" },
        )
    }
}

@Composable
private fun CartRow(name: String, price: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(name, style = TextStyle(fontSize = 16.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(price, style = TextStyle(fontSize = 16.sp))
            // seeded bug 1: tappable, but nothing tells a screen reader (or an agent) what it does
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .background(Color(0xFFE0E0E0), CircleShape)
                    .clickable { },
            )
        }
    }
}

/** Animation fixture for the "wait until the screen is stable" detector. */
@Composable
fun LoadingSpinner() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha(alpha)
                    .background(Color(0xFF1F5FBF), CircleShape),
            )
            BasicText("Loading…", style = TextStyle(fontSize = 16.sp), modifier = Modifier.padding(top = 12.dp))
        }
    }
}
