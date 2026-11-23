package dev.spikeai.guard

import kotlin.math.abs
import kotlin.math.roundToInt

/** A deterministic check. No LLM, no network: runs in milliseconds on every PR. */
interface Rule {
    val id: String
    fun check(screen: Screen, rows: List<Row>): List<Finding>
}

private fun List<Row>.firstOf(kind: RowKind) = firstOrNull { it.kind == kind }

/** Line items above the first summary row. Only items whose price is positive and a single amount. */
private fun List<Row>.items(): List<Row> {
    val firstSummary = firstOrNull { it.kind in setOf(RowKind.SUBTOTAL, RowKind.TOTAL, RowKind.DELIVERY, RowKind.DISCOUNT) }
    return filter { it.kind == RowKind.ITEM && (firstSummary == null || it.top < firstSummary.top) }
}

private fun Long.inr(): String = "₹" + "%,.2f".format(this / 100.0).removeSuffix(".00")

/** Cart line items must add up to the subtotal. */
object CartSumRule : Rule {
    override val id = "cart-sum"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> {
        val items = rows.items()
        val subtotal = rows.firstOf(RowKind.SUBTOTAL)?.amount ?: return emptyList()
        if (items.isEmpty()) return emptyList()
        val sum = items.sumOf { it.amount!!.paise }
        return if (sum != subtotal.paise) {
            listOf(Finding(screen.name, id, "Items add up to ${sum.inr()} but subtotal shows $subtotal"))
        } else emptyList()
    }
}

/** total = (subtotal or sum of items) + delivery − discounts. */
object TotalEquationRule : Rule {
    override val id = "total-equation"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> {
        val total = rows.firstOf(RowKind.TOTAL)?.amount ?: return emptyList()
        val base = rows.firstOf(RowKind.SUBTOTAL)?.amount?.paise
            ?: rows.items().takeIf { it.isNotEmpty() }?.sumOf { it.amount!!.paise }
            ?: return emptyList()
        val delivery = rows.filter { it.kind == RowKind.DELIVERY }.sumOf { it.amount?.paise ?: 0 }
        val discount = rows.filter { it.kind == RowKind.DISCOUNT }.sumOf { abs(it.amount?.paise ?: 0) }
        val expected = base + delivery - discount
        return if (expected != total.paise) {
            listOf(
                Finding(
                    screen.name, id,
                    "Total shows $total but ${base.inr()} + delivery ${delivery.inr()} − discount ${discount.inr()} = ${expected.inr()}",
                ),
            )
        } else emptyList()
    }
}

/** The amount on the Pay / Place order button must equal the displayed total. */
object PayButtonRule : Rule {
    override val id = "pay-button-mismatch"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> {
        val total = rows.firstOf(RowKind.TOTAL)?.amount ?: return emptyList()
        return rows.filter { it.kind == RowKind.PAY_CTA }.mapNotNull { row ->
            val pay = row.amount ?: return@mapNotNull null
            if (pay.paise != total.paise) Finding(screen.name, id, "Pay button says $pay but total is $total") else null
        }
    }
}

/** "You save ₹X" must match the discounts shown (cart) or MRP − price (product page). */
object SavingsRule : Rule {
    override val id = "savings-mismatch"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> {
        val savings = rows.firstOf(RowKind.SAVINGS)?.amount ?: return emptyList()
        val discounts = rows.filter { it.kind == RowKind.DISCOUNT }.sumOf { abs(it.amount?.paise ?: 0) }
        val expected = if (discounts > 0) discounts else mrpAndPrice(rows)?.let { (mrp, price) -> mrp - price } ?: return emptyList()
        return if (abs(savings.paise) != expected) {
            listOf(Finding(screen.name, id, "Says you save $savings but discounts add up to ${expected.inr()}"))
        } else emptyList()
    }
}

/** Product page: "N% off" must match MRP and selling price (±1% for rounding). */
object DiscountPercentRule : Rule {
    override val id = "discount-percent"
    private val percent = Regex("""(\d{1,2})\s?%\s?off""", RegexOption.IGNORE_CASE)

    override fun check(screen: Screen, rows: List<Row>): List<Finding> {
        val (mrp, price) = mrpAndPrice(rows) ?: return emptyList()
        val stated = screen.nodes.firstNotNullOfOrNull { n -> n.label?.let { percent.find(it) } }
            ?.groupValues?.get(1)?.toInt() ?: return emptyList()
        val actual = ((mrp - price) * 100.0 / mrp).roundToInt()
        return if (abs(actual - stated) > 1) {
            listOf(Finding(screen.name, id, "Shows $stated% off but MRP ${mrp.inr()} → price ${price.inr()} is $actual% off"))
        } else emptyList()
    }
}

/** MRP and selling price on the MRP row: the MRP node's amount, and another amount on the same row. */
private fun mrpAndPrice(rows: List<Row>): Pair<Long, Long>? {
    val row = rows.firstOrNull { it.kind == RowKind.MRP } ?: return null
    val mrpNode = row.nodes.firstOrNull { it.label?.lowercase()?.contains("mrp") == true } ?: return null
    val mrp = MoneyParser.findAll(mrpNode.label).firstOrNull()?.paise ?: return null
    val price = row.nodes.filter { it !== mrpNode }.flatMap { MoneyParser.findAll(it.label) }.firstOrNull()?.paise ?: return null
    return mrp to price
}

/** One currency style per screen, and thousands separators on amounts ≥ 1,000. */
object CurrencyFormatRule : Rule {
    override val id = "currency-format"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> {
        val all = screen.nodes.flatMap { MoneyParser.findAll(it.label) }
        val out = mutableListOf<Finding>()
        val symbols = all.map { it.symbol }.toSet()
        if (symbols.size > 1) out += Finding(screen.name, id, "Mixed currency styles on one screen: ${symbols.joinToString()}")
        all.filter { it.missingSeparator }.forEach { out += Finding(screen.name, id, "Missing thousands separator: \"$it\"") }
        return out
    }
}

/** Every tappable element needs a label — for TalkBack users and for any test or agent. */
object UnlabelledClickableRule : Rule {
    override val id = "unlabelled-clickable"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> =
        screen.nodes.filter { it.isClickable && it.label.isNullOrBlank() }.map {
            Finding(screen.name, id, "Tappable element [${it.id}] has no label", listOf(it.id))
        }
}

/** Negative amounts are only allowed on discount/savings rows. (₹0 is a valid balance or reward.) */
object NegativePriceRule : Rule {
    override val id = "negative-price"
    override fun check(screen: Screen, rows: List<Row>): List<Finding> =
        rows.filter { it.kind !in setOf(RowKind.DISCOUNT, RowKind.SAVINGS) }
            .flatMap { row -> row.amounts.filter { it.paise < 0 }.map { row to it } }
            .map { (row, m) -> Finding(screen.name, id, "Negative amount $m on \"${row.text}\"") }
}

object CommerceGuard {
    val rules: List<Rule> = listOf(
        CartSumRule, TotalEquationRule, PayButtonRule, SavingsRule,
        DiscountPercentRule, CurrencyFormatRule, UnlabelledClickableRule, NegativePriceRule,
    )

    fun check(screen: Screen): List<Finding> {
        val rows = Row.group(screen.nodes)
        return rules.flatMap { it.check(screen, rows) }
    }
}
