package com.volley.manager.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val isGuest: Boolean = false,
    val email: String = "",
    val phone: String = "",
    val heightCm: Int? = null,
    val jerseyNumber: Int? = null,
    val notes: String = "",
    val serviceRating: Int = 0,
    val receptionRating: Int = 0,
    val settingRating: Int = 0,
    val attackRating: Int = 0,
    val blockRating: Int = 0,
    val defenseRating: Int = 0,
    val motivationRating: Int = 0,
    val techniqueRating: Int = 0
)

@Entity(tableName = "events")
data class VolleyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: EventType,
    val startsAt: Long,
    val durationMinutes: Int,
    val recurrenceDays: String = "",
    val recurrenceEndAt: Long? = null,
    val cancelled: Boolean = false
)

@Entity(tableName = "event_guests", primaryKeys = ["playerId", "eventId"])
data class EventGuest(val playerId: Long, val eventId: Long)

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

@Entity(tableName = "feedback")
data class Feedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val title: String,
    val details: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY lastName, firstName")
    fun observeAll(): Flow<List<Player>>
    @Insert suspend fun insert(player: Player): Long
    @Update suspend fun update(player: Player)
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
interface EventGuestDao {
    @Query("SELECT * FROM event_guests")
    fun observeAll(): Flow<List<EventGuest>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(guest: EventGuest)
    @Delete suspend fun remove(guest: EventGuest)
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

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Feedback>>
    @Insert suspend fun insert(feedback: Feedback)
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN email TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE players ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE players ADD COLUMN heightCm INTEGER")
        db.execSQL("ALTER TABLE players ADD COLUMN jerseyNumber INTEGER")
        db.execSQL("ALTER TABLE players ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN serviceRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN receptionRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN settingRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN attackRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN blockRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN defenseRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN motivationRating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN techniqueRating INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Player::class, VolleyEvent::class, EventGuest::class, Attendance::class, Absence::class, Feedback::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun players(): PlayerDao
    abstract fun events(): EventDao
    abstract fun eventGuests(): EventGuestDao
    abstract fun attendance(): AttendanceDao
    abstract fun absences(): AbsenceDao
    abstract fun feedback(): FeedbackDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context, AppDatabase::class.java, "volley-manager.db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6).build()
    }
}

class Converters {
    @TypeConverter fun eventType(value: EventType) = value.name
    @TypeConverter fun eventType(value: String) = EventType.valueOf(value)
    @TypeConverter fun attendanceStatus(value: AttendanceStatus) = value.name
    @TypeConverter fun attendanceStatus(value: String) = AttendanceStatus.valueOf(value)
}
