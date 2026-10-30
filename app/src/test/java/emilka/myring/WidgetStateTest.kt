package emilka.myring

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Logika „ile dni zostało” — w tym przełom północy (daty kalendarzowe). */
class WidgetStateTest {

    @Test
    fun daysLeftBeforeRemove() {
        val insert = LocalDate.of(2026, 10, 1)
        val today = LocalDate.of(2026, 10, 15)
        val state = WidgetLogic.compute(today, insert, 3, null)
        // wyjęcie = 1 + 21 = 22.10 → 7 dni
        assertEquals("7", state.daysText)
        assertEquals("do wyjęcia", state.label)
    }

    @Test
    fun todayIsRemoveDay() {
        val insert = LocalDate.of(2026, 10, 1)
        val remove = insert.plusDays(21)
        val state = WidgetLogic.compute(remove, insert, 3, null)
        assertEquals("dziś", state.daysText)
        assertEquals("wyjmij krążek", state.label)
    }

    @Test
    fun afterMidnightDayCountDrops() {
        val insert = LocalDate.of(2026, 10, 1)
        val before = LocalDate.of(2026, 10, 21)
        val afterMidnight = LocalDate.of(2026, 10, 22)
        val s1 = WidgetLogic.compute(before, insert, 3, null)
        val s2 = WidgetLogic.compute(afterMidnight, insert, 3, null)
        assertEquals("1", s1.daysText)
        assertEquals("dziś", s2.daysText)
    }

    @Test
    fun overdueShowsPoTerminie() {
        val insert = LocalDate.of(2026, 10, 1)
        val overdue = LocalDate.of(2026, 10, 25)
        val state = WidgetLogic.compute(overdue, insert, 3, null)
        assertEquals("3", state.daysText)
        assertEquals("po terminie", state.label)
    }

    @Test
    fun breakUntilInsert() {
        val next = LocalDate.of(2026, 10, 29)
        val today = LocalDate.of(2026, 10, 25)
        val state = WidgetLogic.compute(today, LocalDate.of(2026, 10, 1), 3, next)
        assertEquals("4", state.daysText)
        assertEquals("do włożenia", state.label)
    }

    @Test
    fun configurePlaceholderIsSeparateFromLogic() {
        // Sam brak startDate obsługuje provider; tu tylko sanity daty.
        val state = WidgetLogic.compute(
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 10, 30),
            3,
            null
        )
        assertEquals("21", state.daysText)
    }
}
