package com.example.scheduler

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure Kotlin 5-field Cron parser: minute hour day-of-month month day-of-week
 * Supports `*`, step intervals (`* / n`), single values `5`, ranges `1-5`, and comma lists `1,3,5`.
 */
object CronParser {

    data class CronSpec(
        val minutes: Set<Int>,
        val hours: Set<Int>,
        val daysOfMonth: Set<Int>,
        val months: Set<Int>,
        val daysOfWeek: Set<Int>, // 0=Sunday..6=Saturday (7 normalized to 0)
    )

    fun isValid(expression: String): Boolean = runCatching { parse(expression) }.isSuccess

    fun parse(expression: String): CronSpec {
        val parts = expression.trim().split(Regex("\\s+"))
        require(parts.size == 5) { "Ekspresi cron harus memiliki 5 bagian: menit jam tanggal bulan hari" }
        val minutes = parseField(parts[0], 0, 59)
        val hours = parseField(parts[1], 0, 23)
        val dom = parseField(parts[2], 1, 31)
        val months = parseField(parts[3], 1, 12)
        val dowRaw = parseField(parts[4], 0, 7)
        val dow = dowRaw.map { if (it == 7) 0 else it }.toSet()
        return CronSpec(minutes, hours, dom, months, dow)
    }

    fun nextRunMillis(
        expression: String,
        fromMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val spec = parse(expression)
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }

        // Search up to 366 days ahead (527,040 minutes)
        repeat(366 * 24 * 60) {
            val minute = cal.get(Calendar.MINUTE)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dom = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH) + 1 // 1..12
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 -> 0

            if (minute in spec.minutes &&
                hour in spec.hours &&
                dom in spec.daysOfMonth &&
                month in spec.months &&
                dow in spec.daysOfWeek
            ) {
                return cal.timeInMillis
            }
            cal.add(Calendar.MINUTE, 1)
        }
        return fromMillis + 24 * 60 * 60 * 1000L
    }

    fun describe(expression: String): String {
        val parts = expression.trim().split(Regex("\\s+"))
        if (parts.size != 5) return expression
        val (min, hour, dom, month, dow) = parts
        return when {
            min.startsWith("*/") && hour == "*" && dom == "*" && month == "*" && dow == "*" ->
                "Setiap ${min.removePrefix("*/")} menit"
            hour.startsWith("*/") && dom == "*" && month == "*" && dow == "*" ->
                "Setiap ${hour.removePrefix("*/")} jam (menit $min)"
            dom == "*" && month == "*" && dow == "*" && min.all(Char::isDigit) && hour.all(Char::isDigit) ->
                "Setiap hari pukul ${hour.padStart(2, '0')}:${min.padStart(2, '0')}"
            dom == "*" && month == "*" && dow == "1-5" && min.all(Char::isDigit) && hour.all(Char::isDigit) ->
                "Senin–Jumat pukul ${hour.padStart(2, '0')}:${min.padStart(2, '0')}"
            else -> "Cron: $expression"
        }
    }

    private fun parseField(field: String, min: Int, max: Int): Set<Int> {
        val result = linkedSetOf<Int>()
        for (segment in field.split(",")) {
            val trimmed = segment.trim()
            require(trimmed.isNotEmpty())
            val slashParts = trimmed.split("/")
            require(slashParts.size <= 2)
            val step = if (slashParts.size == 2) {
                slashParts[1].toInt().also { require(it > 0) }
            } else 1

            val rangePart = slashParts[0]
            val (start, end) = when {
                rangePart == "*" -> min to max
                rangePart.contains("-") -> {
                    val bounds = rangePart.split("-")
                    require(bounds.size == 2)
                    bounds[0].toInt() to bounds[1].toInt()
                }
                else -> {
                    val v = rangePart.toInt()
                    v to v
                }
            }
            require(start in min..max && end in min..max && start <= end) {
                "Nilai cron di luar batas ($min..$max): $trimmed"
            }
            var cur = start
            while (cur <= end) {
                result.add(cur)
                cur += step
            }
        }
        return result
    }
}
