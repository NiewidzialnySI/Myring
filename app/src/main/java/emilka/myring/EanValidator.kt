package emilka.myring

/** Wspólna walidacja EAN-8 / EAN-13 (długość + cyfry + checksum). */
object EanValidator {
    fun isValid(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length != 8 && trimmed.length != 13) return false
        if (!trimmed.all { it.isDigit() }) return false
        val digits = trimmed.map { it - '0' }
        val check = digits.last()
        val body = digits.dropLast(1)
        // Od prawej (bez check): waga 3,1,3,1…
        var sum = 0
        body.asReversed().forEachIndexed { idx, d ->
            sum += if (idx % 2 == 0) d * 3 else d
        }
        val calc = (10 - (sum % 10)) % 10
        return calc == check
    }
}
