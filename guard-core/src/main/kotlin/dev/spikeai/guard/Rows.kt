package dev.spikeai.guard

/** What a horizontal row of a commerce screen represents, judged by its words. */
enum class RowKind { PAY_CTA, SAVINGS, SUBTOTAL, DISCOUNT, DELIVERY, TOTAL, MRP, ITEM, OTHER }

class Row(val nodes: List<UiNode>) {
    val text: String = nodes.mapNotNull { it.label }.joinToString(" ")
    val amounts: List<Money> = nodes.flatMap { MoneyParser.findAll(it.label) }
    val words: String = MoneyParser.stripAmounts(text)
    val top: Int = nodes.minOf { it.top }
    val kind: RowKind = classify()

    /** The amount a summary row stands for (last amount on the row, e.g. "Subtotal ₹2,498"). */
    val amount: Money? get() = amounts.lastOrNull()

    private fun classify(): RowKind {
        if (amounts.isEmpty()) return RowKind.OTHER
        val w = words
        return when {
            nodes.any { it.isClickable && MoneyParser.findAll(it.label).isNotEmpty() } &&
                (w.contains("pay") || w.contains("place order")) -> RowKind.PAY_CTA
            w.contains("you save") || w.contains("savings") -> RowKind.SAVINGS
            w.contains("subtotal") || w.contains("item total") || w.contains("bag total") || w.contains("cart total") -> RowKind.SUBTOTAL
            w.contains("discount") || w.contains("coupon") || w.contains("offer") -> RowKind.DISCOUNT
            w.contains("delivery") || w.contains("shipping") -> RowKind.DELIVERY
            w.contains("total") || w.contains("to pay") || w.contains("payable") -> RowKind.TOTAL
            w.contains("mrp") -> RowKind.MRP
            else -> RowKind.ITEM
        }
    }

    companion object {
        /** Groups nodes whose vertical centres fall inside the same band into one row. */
        fun group(nodes: List<UiNode>): List<Row> {
            val bands = mutableListOf<MutableList<UiNode>>()
            for (n in nodes.sortedBy { it.top }) {
                val band = bands.lastOrNull()
                if (band != null && n.centerY in band.minOf { it.top }..band.maxOf { it.bottom }) band += n
                else bands += mutableListOf(n)
            }
            return bands.map { Row(it) }
        }
    }
}
