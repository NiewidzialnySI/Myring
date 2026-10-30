package emilka.myring

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Czysta logika widgetu (bez Androida) — testowalna na JVM. */
object WidgetLogic {
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    data class State(val daysText: String, val label: String, val dateText: String)

    /**
     * Stan na dany dzień kalendarzowy.
     * ChronoUnit.DAYS.between liczy pełne daty — po północy liczba dni spada o 1.
     */
    fun compute(
        today: LocalDate,
        currentInsert: LocalDate,
        weeks: Int,
        nextInsert: LocalDate?
    ): State {
        return if (nextInsert != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, nextInsert)
            when {
                days > 0 -> State(days.toString(), "do włożenia", nextInsert.format(DATE_FMT))
                days == 0L -> State("dziś", "włóż krążek", nextInsert.format(DATE_FMT))
                else -> State("${-days}", "po terminie", nextInsert.format(DATE_FMT))
            }
        } else {
            val remove = currentInsert.plusDays(weeks * 7L)
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, remove)
            when {
                days > 0 -> State(days.toString(), "do wyjęcia", remove.format(DATE_FMT))
                days == 0L -> State("dziś", "wyjmij krążek", remove.format(DATE_FMT))
                else -> State("${-days}", "po terminie", remove.format(DATE_FMT))
            }
        }
    }
}
