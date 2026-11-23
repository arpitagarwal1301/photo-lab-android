package dev.spikeai.fixtures.shop

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Each screen has a clean preview (must produce zero findings) and a buggy one with planted bugs.
// The planted bugs are listed in src/test/resources/expected-bugs.txt.

@Preview @Composable
fun CartClean() = CartScreen(
    lines = listOf(
        CartLine("Blue light glasses", "₹1,999", "Remove Blue light glasses"),
        CartLine("Lens cleaning kit", "₹499", "Remove Lens cleaning kit"),
    ),
    subtotal = "₹2,498",
)

@Preview @Composable
fun CartBuggy() = CartScreen(
    lines = listOf(
        CartLine("Blue light glasses", "₹1999", null), // bug: no thousands separator; bug: remove has no label
        CartLine("Lens cleaning kit", "₹499", null),
    ),
    subtotal = "₹2,598", // bug: items add up to ₹2,498
)

@Preview @Composable
fun CheckoutClean() = CheckoutScreen(
    subtotal = "₹2,498", delivery = "₹49", coupon = "Coupon SAVE10", discount = "-₹250",
    total = "₹2,297", youSave = "You save ₹250", pay = "Pay ₹2,297",
)

@Preview @Composable
fun CheckoutBuggy() = CheckoutScreen(
    subtotal = "₹2,498", delivery = "₹49", coupon = "Coupon SAVE10", discount = "-₹250",
    total = "₹2,248", // bug: delivery fee left out of the total
    youSave = "You save ₹200", // bug: discount is ₹250
    pay = "Pay ₹2,297", // bug: button charges a different amount than the total shown
)

@Preview @Composable
fun ProductClean() = ProductScreen(
    name = "Round blue light glasses", price = "₹1,500", mrp = "MRP ₹2,000", off = "25% off", cta = "Add to cart",
)

@Preview @Composable
fun ProductBuggy() = ProductScreen(
    name = "Round blue light glasses",
    price = "₹1,500",
    mrp = "MRP Rs. 2,000", // bug: mixed currency style
    off = "30% off", // bug: it's 25% off
    cta = "", // bug: CTA rendered with an empty string (missing translation)
)

@Preview @Composable
fun OrderClean() = OrderScreen(
    orderId = "#10234",
    lines = listOf("Blue light glasses" to "₹1,500", "Anti-glare coating" to "₹450"),
    total = "₹1,950",
)

@Preview @Composable
fun OrderBuggy() = OrderScreen(
    orderId = "#10235",
    lines = listOf("Blue light glasses" to "₹1,500", "Lens upgrade" to "₹-50"), // bug: negative item price
    total = "₹1,450",
)
