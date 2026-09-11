package com.volley.manager.domain

import com.volley.manager.data.Attendance
import com.volley.manager.data.AttendanceStatus
import com.volley.manager.data.VolleyEvent

fun absenceRate(playerId: Long, sessions: List<VolleyEvent>, attendance: List<Attendance>): Int {
    val absent = sessions.count { event ->
        attendance.any { it.playerId == playerId && it.eventId == event.id && it.status == AttendanceStatus.ABSENT }
    }
    return percentage(absent, sessions.size)
}

fun collectiveAbsenceRate(
    playerIds: List<Long>,
    sessions: List<VolleyEvent>,
    attendance: List<Attendance>
): Int {
    val absent = playerIds.sumOf { playerId ->
        sessions.count { event ->
            attendance.any { it.playerId == playerId && it.eventId == event.id && it.status == AttendanceStatus.ABSENT }
        }
    }
    return percentage(absent, playerIds.size * sessions.size)
}

fun percentage(value: Int, total: Int): Int = if (total == 0) 0 else value * 100 / total
