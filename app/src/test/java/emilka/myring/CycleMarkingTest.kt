package emilka.myring

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CycleMarkingTest {

    @Test
    fun polishPlural() {
        assertEquals("1 krążek", CycleMarking.polishRingsCount(1))
        assertEquals("2 krążki", CycleMarking.polishRingsCount(2))
        assertEquals("5 krążków", CycleMarking.polishRingsCount(5))
        assertEquals("22 krążki", CycleMarking.polishRingsCount(22))
    }

    @Test
    fun stockEstimateIncludesCurrentPhase() {
        val today = LocalDate.of(2026, 1, 10)
        val insert = LocalDate.of(2026, 1, 1)
        val end = CycleMarking.estimateStockEndDate(
            today, insert, 3, null, null, 2
        )
        val expected = today.plusDays(
            computeCycle(insert, 3, today).daysLeft + (3 * 7L + 7L)
        )
        assertEquals(expected, end)
    }
}