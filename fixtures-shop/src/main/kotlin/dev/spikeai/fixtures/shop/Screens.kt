package dev.spikeai.fixtures.shop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CartLine(val name: String, val price: String, val removeLabel: String?)

@Composable
fun CartScreen(lines: List<CartLine>, subtotal: String) = ShopScreen("Cart") {
    lines.forEach { PriceRow(it.name, it.price) { IconButton(it.removeLabel) } }
    PriceRow("Subtotal", subtotal)
    PrimaryButton("Checkout")
}

@Composable
fun CheckoutScreen(
    subtotal: String,
    delivery: String,
    coupon: String,
    discount: String,
    total: String,
    youSave: String,
    pay: String,
) = ShopScreen("Checkout") {
    PriceRow("Subtotal", subtotal)
    PriceRow("Delivery", delivery)
    PriceRow(coupon, discount)
    PriceRow("Order total", total)
    Note(youSave)
    PrimaryButton(pay)
}

@Composable
fun ProductScreen(name: String, price: String, mrp: String, off: String, cta: String) = ShopScreen(name) {
    Row {
        BasicText(price, style = TextStyle(fontSize = 22.sp))
        Spacer(Modifier.width(12.dp))
        BasicText(mrp, style = TextStyle(fontSize = 14.sp))
        Spacer(Modifier.width(12.dp))
        BasicText(off, style = TextStyle(fontSize = 14.sp))
    }
    Note("Free delivery by Thursday")
    PrimaryButton(cta)
}

@Composable
fun OrderScreen(orderId: String, lines: List<Pair<String, String>>, total: String) = ShopScreen("Order $orderId") {
    lines.forEach { (name, price) -> PriceRow(name, price) }
    PriceRow("Total", total)
    PrimaryButton("Track order")
}
