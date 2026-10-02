package com.kekitemkekifalta.core

/** Time-derived state of an item that is at home. */
enum class StockStatus(val label: String) {
    HAVE("tem"),
    RUNNING_LOW("acabando"),
    PROBABLY_OUT("provavelmente acabou"),
}

data class StockState(
    val status: StockStatus,
    /** elapsed / expected; 1.0 means the expected duration is over. */
    val progress: Double,
    val elapsedDays: Double,
    val expectedDays: Double,
    /** Negative when past the expected duration. */
    val remainingDays: Double,
    /** True when a "ainda tem" is holding a PROBABLY_OUT item back as RUNNING_LOW. */
    val snoozed: Boolean,
)

object StockClock {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val RUNNING_LOW_AT = 0.75
    const val PROBABLY_OUT_AT = 1.0

    fun elapsedDays(since: Long, now: Long): Double = ((now - since).coerceAtLeast(0L)).toDouble() / DAY_MS

    fun state(stockedAt: Long, now: Long, expectedDays: Double, snoozedUntil: Long? = null): StockState {
        val elapsed = elapsedDays(stockedAt, now)
        val expected = expectedDays.coerceAtLeast(DurationEstimator.MIN_DAYS)
        val progress = elapsed / expected
        val snoozed = snoozedUntil != null && now < snoozedUntil
        val status = when {
            progress >= PROBABLY_OUT_AT && !snoozed -> StockStatus.PROBABLY_OUT
            progress >= RUNNING_LOW_AT -> StockStatus.RUNNING_LOW
            else -> StockStatus.HAVE
        }
        return StockState(status, progress, elapsed, expected, expected - elapsed, snoozed && progress >= PROBABLY_OUT_AT)
    }

    /** Short pt-BR phrase for the time left, e.g. "faltam ~3 dias", "acaba hoje", "passou 2 dias". */
    fun remainingLabel(state: StockState): String {
        val r = state.remainingDays
        return when {
            r >= 1.5 -> "faltam ~${Math.round(r)} dias"
            r >= 0.5 -> "falta ~1 dia"
            r > -0.5 -> "acaba hoje"
            r > -1.5 -> "passou 1 dia"
            else -> "passou ${Math.round(-r)} dias"
        }
    }
}
