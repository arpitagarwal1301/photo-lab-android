package dev.spikeai.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyParserTest {
    @Test fun parsesRupeeFormats() {
        val m = MoneyParser.findAll("Pay ₹1,499 · was Rs. 2,000 · coupon -₹250 · refund ₹-50 · ₹12.5")
        assertEquals(listOf(149_900L, 200_000L, -25_000L, -5_000L, 1_250L), m.map { it.paise })
        assertEquals(listOf("₹", "Rs", "₹", "₹", "₹"), m.map { it.symbol })
    }

    @Test fun flagsMissingSeparator() {
        assertTrue(MoneyParser.findAll("₹1999").single().missingSeparator)
        assertTrue(!MoneyParser.findAll("₹1,999").single().missingSeparator)
        assertTrue(!MoneyParser.findAll("₹999").single().missingSeparator)
    }
}

class RulesTest {
    private var nextId = 1
    private fun row(y: Int, vararg labels: String?, clickable: Boolean = false) = labels.mapIndexed { i, l ->
        UiNode(nextId++, if (clickable) "button" else "text", l, i * 300, y, i * 300 + 200, y + 50)
    }

    @Test fun cleanCheckoutHasNoFindings() {
        val s = Screen(
            "clean",
            row(0, "Blue light glasses", "₹1,999") + row(100, "Lens kit", "₹499") +
                row(200, "Subtotal", "₹2,498") + row(300, "Delivery", "₹49") +
                row(400, "Coupon SAVE10", "-₹250") + row(500, "Order total", "₹2,297") +
                row(600, "You save ₹250") + row(700, "Pay ₹2,297", clickable = true),
        )
        assertEquals(emptyList<Finding>(), CommerceGuard.check(s))
    }

    @Test fun catchesTotalAndPayMismatch() {
        val s = Screen(
            "buggy",
            row(0, "Subtotal", "₹2,498") + row(100, "Delivery", "₹49") + row(200, "Coupon", "-₹250") +
                row(300, "Order total", "₹2,248") + row(400, "Pay ₹2,297", clickable = true),
        )
        val ids = CommerceGuard.check(s).map { it.ruleId }.toSet()
        assertEquals(setOf("total-equation", "pay-button-mismatch"), ids)
    }
}
