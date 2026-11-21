package emilka.myring

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Oznaczenia kalendarza i prognoza zapasu z uwzględnieniem potwierdzonych dat wyjęcia/włożenia.
 */
object CycleMarking {

    fun dayMark(
        date: LocalDate,
        insertAnchor: LocalDate,
        weeks: Int,
        nextInsert: LocalDate? = null,
        lastRemoved: LocalDate? = null
    ): DayMark {
        if (nextInsert != null && lastRemoved != null) {
            when {
                date.isBefore(insertAnchor) -> return DayMark.NONE
                date.isBefore(lastRemoved) -> return DayMark.RING
                date.isBefore(nextInsert) -> return DayMark.BREAK
                date == nextInsert -> return DayMark.INSERT
                else -> {
                    val step = weeks * 7L + 7L
                    val delta = ChronoUnit.DAYS.between(nextInsert, date)
                    if (delta < 0) return DayMark.NONE
                    val pos = delta % step
                    val weeksDays = weeks * 7L
                    return when {
                        pos == 0L -> DayMark.INSERT
                        pos < weeksDays -> DayMark.RING
                        pos == weeksDays -> DayMark.BREAK
                        pos < weeksDays + 7L -> DayMark.BREAK
                        else -> DayMark.RING
                    }
                }
            }
        }
        return emilka.myring.dayMark(date, insertAnchor, weeks)
    }

    private fun cycleLengthDays(weeks: Int): Long = weeks * 7L + 7L

    /** Szacuje datę wyczerpania zapasu od dziś (reszta bieżącej fazy + pełne cykle). */
    fun estimateStockEndDate(
        today: LocalDate,
        insertAnchor: LocalDate,
        weeks: Int,
        nextInsert: LocalDate?,
        lastRemoved: LocalDate?,
        ringsRemaining: Int
    ): LocalDate? {
        if (ringsRemaining <= 0) return null
        val phaseLeft = when {
            nextInsert != null && lastRemoved != null ->
                ChronoUnit.DAYS.between(today, nextInsert).coerceAtLeast(0)
            else -> computeCycle(insertAnchor, weeks, today).daysLeft.coerceAtLeast(0)
        }
        val fullCycles = (ringsRemaining - 1).coerceAtLeast(0)
        return today.plusDays(phaseLeft + fullCycles * cycleLengthDays(weeks))
    }

    fun polishRingsCount(n: Int): String = when {
        n == 1 -> "1 krążek"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n krążki"
        else -> "$n krążków"
    }
}