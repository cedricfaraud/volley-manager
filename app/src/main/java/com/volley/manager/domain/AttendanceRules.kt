package com.volley.manager.domain

import com.volley.manager.data.Attendance
import com.volley.manager.data.AttendanceStatus
import com.volley.manager.data.VolleyEvent

data class AbsenceBreakdown(
    val totalRate: Int,
    val justifiedRate: Int
)

fun absenceBreakdown(playerId: Long, sessions: List<VolleyEvent>, attendance: List<Attendance>): AbsenceBreakdown {
    val playerAttendance = attendance.filter { it.playerId == playerId }
    val absent = sessions.count { event ->
        playerAttendance.any {
            it.eventId == event.id &&
                (it.status == AttendanceStatus.ABSENT || it.status == AttendanceStatus.EXCUSED)
        }
    }
    val justified = sessions.count { event ->
        playerAttendance.any { it.eventId == event.id && it.status == AttendanceStatus.EXCUSED }
    }
    return AbsenceBreakdown(
        totalRate = percentage(absent, sessions.size),
        justifiedRate = percentage(justified, sessions.size)
    )
}

fun absenceRate(playerId: Long, sessions: List<VolleyEvent>, attendance: List<Attendance>): Int {
    return absenceBreakdown(playerId, sessions, attendance).totalRate
}

fun collectiveAbsenceBreakdown(
    playerIds: List<Long>,
    sessions: List<VolleyEvent>,
    attendance: List<Attendance>
): AbsenceBreakdown {
    val collectiveAttendance = attendance.filter { it.playerId in playerIds }
    val absent = playerIds.sumOf { playerId ->
        sessions.count { event ->
            collectiveAttendance.any {
                it.playerId == playerId &&
                    it.eventId == event.id &&
                    (it.status == AttendanceStatus.ABSENT || it.status == AttendanceStatus.EXCUSED)
            }
        }
    }
    val justified = playerIds.sumOf { playerId ->
        sessions.count { event ->
            collectiveAttendance.any {
                it.playerId == playerId && it.eventId == event.id && it.status == AttendanceStatus.EXCUSED
            }
        }
    }
    return AbsenceBreakdown(
        totalRate = percentage(absent, playerIds.size * sessions.size),
        justifiedRate = percentage(justified, playerIds.size * sessions.size)
    )
}

fun collectiveAbsenceRate(
    playerIds: List<Long>,
    sessions: List<VolleyEvent>,
    attendance: List<Attendance>
): Int {
    return collectiveAbsenceBreakdown(playerIds, sessions, attendance).totalRate
}

fun percentage(value: Int, total: Int): Int = if (total == 0) 0 else value * 100 / total
