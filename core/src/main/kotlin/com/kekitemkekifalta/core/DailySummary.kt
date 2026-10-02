package com.kekitemkekifalta.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object DailySummary {
    /** "3 itens acabando, 2 provavelmente acabaram"; null when there is nothing to say. */
    fun message(runningLow: Int, probablyOut: Int): String? {
        val parts = buildList {
            if (runningLow > 0) add(if (runningLow == 1) "1 item acabando" else "$runningLow itens acabando")
            if (probablyOut > 0) add(if (probablyOut == 1) "1 provavelmente acabou" else "$probablyOut provavelmente acabaram")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** Milliseconds from [nowMillis] until the next [minuteOfDay] (local time); never zero. */
    fun delayUntilNext(nowMillis: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))
        var next = now.toLocalDate().atTime(time).atZone(zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return next.toInstant().toEpochMilli() - nowMillis
    }

    /** Local calendar day number, used to send at most one notification per day. */
    fun epochDay(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
}
