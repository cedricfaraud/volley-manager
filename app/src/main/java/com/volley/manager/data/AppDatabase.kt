package com.volley.manager.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class EventType { TRAINING, MATCH, EXCEPTIONAL }
enum class AttendanceStatus { PRESENT, ABSENT, LATE, EXCUSED }

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val position: String,
    val isGuest: Boolean = false
)

@Entity(tableName = "events")
data class VolleyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: EventType,
    val startsAt: Long,
    val durationMinutes: Int,
    val recurrence: String = "Aucune",
    val cancelled: Boolean = false
)

@Entity(tableName = "attendance", primaryKeys = ["playerId", "eventId"])
data class Attendance(
    val playerId: Long,
    val eventId: Long,
    val status: AttendanceStatus = AttendanceStatus.PRESENT
)

@Entity(tableName = "absences")
data class Absence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: Long,
    val startsAt: Long,
    val endsAt: Long,
    val reason: String
)

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY lastName, firstName")
    fun observeAll(): Flow<List<Player>>
    @Insert suspend fun insert(player: Player)
    @Delete suspend fun delete(player: Player)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startsAt")
    fun observeAll(): Flow<List<VolleyEvent>>
    @Insert suspend fun insert(event: VolleyEvent)
    @Update suspend fun update(event: VolleyEvent)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance")
    fun observeAll(): Flow<List<Attendance>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(attendance: Attendance)
}

@Dao
interface AbsenceDao {
    @Insert suspend fun insert(absence: Absence)
    @Query("SELECT * FROM absences")
    fun observeAll(): Flow<List<Absence>>
}

@Database(entities = [Player::class, VolleyEvent::class, Attendance::class, Absence::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun players(): PlayerDao
    abstract fun events(): EventDao
    abstract fun attendance(): AttendanceDao
    abstract fun absences(): AbsenceDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context, AppDatabase::class.java, "volley-manager.db"
        ).build()
    }
}

class Converters {
    @TypeConverter fun eventType(value: EventType) = value.name
    @TypeConverter fun eventType(value: String) = EventType.valueOf(value)
    @TypeConverter fun attendanceStatus(value: AttendanceStatus) = value.name
    @TypeConverter fun attendanceStatus(value: String) = AttendanceStatus.valueOf(value)
}
