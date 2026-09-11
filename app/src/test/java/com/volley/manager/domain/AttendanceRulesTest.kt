package com.volley.manager.domain

import com.volley.manager.data.Attendance
import com.volley.manager.data.AttendanceStatus
import com.volley.manager.data.EventType
import com.volley.manager.data.VolleyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceRulesTest {
    private val sessions = listOf(
        VolleyEvent(id = 1, title = "A", type = EventType.TRAINING, startsAt = 1, durationMinutes = 120),
        VolleyEvent(id = 2, title = "B", type = EventType.TRAINING, startsAt = 2, durationMinutes = 120)
    )

    @Test
    fun `sort metric gives 50 percent to one absence out of two`() {
        val attendance = listOf(Attendance(10, 1, AttendanceStatus.ABSENT))
        assertEquals(50, absenceRate(10, sessions, attendance))
    }

    @Test
    fun `collective rate ignores players outside the collective`() {
        val attendance = listOf(
            Attendance(10, 1, AttendanceStatus.ABSENT),
            Attendance(99, 1, AttendanceStatus.ABSENT)
        )
        assertEquals(50, collectiveAbsenceRate(listOf(10), sessions, attendance))
    }

    @Test
    fun `empty history has zero rate`() {
        assertEquals(0, percentage(0, 0))
    }
}
