package dev.spikeai.guard

/** An amount shown on screen, e.g. "₹1,499", "-₹250", "Rs. 2,000". Stored in paise to avoid float errors. */
data class Money(
    val paise: Long,
    val raw: String,
    val symbol: String, // normalised: "₹" | "Rs" | "INR"
    val digits: String, // as written, e.g. "1,499" or "1499"
) {
    val rupees: Double get() = paise / 100.0

    /** Amounts of 1,000 and above should carry a thousands separator. */
    val missingSeparator: Boolean get() = paise >= 100_000 && !digits.contains(',')

    override fun toString(): String = raw
}

object MoneyParser {
    // Optional minus before or after the symbol: "-₹250", "₹-50", "- ₹ 250"
    private val pattern = Regex("""(-)?\s?(₹|Rs\.?|INR)\s?(-)?\s?(\d[\d,]*(?:\.\d{1,2})?)""")

    fun findAll(text: String?): List<Money> {
        if (text.isNullOrBlank()) return emptyList()
        return pattern.findAll(text).map { m ->
            val negative = m.groupValues[1].isNotEmpty() || m.groupValues[3].isNotEmpty()
            val digits = m.groupValues[4]
            val clean = digits.replace(",", "")
            val (whole, frac) = clean.split('.').let { it[0] to it.getOrElse(1) { "0" } }
            val paise = whole.toLong() * 100 + frac.padEnd(2, '0').take(2).toLong()
            val symbol = when {
                m.groupValues[2].startsWith("Rs") -> "Rs"
                m.groupValues[2] == "INR" -> "INR"
                else -> "₹"
            }
            Money(if (negative) -paise else paise, m.value.trim(), symbol, digits)
        }.toList()
    }

    /** Text with all amounts removed, lower-cased — used to classify a row by its words. */
    fun stripAmounts(text: String): String = pattern.replace(text, " ").lowercase()
}
