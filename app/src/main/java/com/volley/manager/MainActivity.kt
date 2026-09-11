@file:OptIn(ExperimentalMaterial3Api::class)

package com.volley.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.volley.manager.data.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val positions = listOf("Libéro", "Passeur", "Pointu", "Central", "R4")
private val weekdays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
private val fullWeekdays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private data class AppPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

private val paletteOptions = listOf(
    Color(0xFF1565C0), Color(0xFF00897B), Color(0xFF6A1B9A),
    Color(0xFFEF6C00), Color(0xFFC62828), Color(0xFF37474F)
)

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(AppDatabase.create(applicationContext)) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VolleyApp(vm) }
    }
}

class MainViewModel(private val db: AppDatabase) : ViewModel() {
    val players = db.players().observeAll()
    val events = db.events().observeAll()
    val attendance = db.attendance().observeAll()
    val eventGuests = db.eventGuests().observeAll()

    fun addPlayer(first: String, last: String, age: Int, position: String, guest: Boolean) =
        viewModelScope.launch { db.players().insert(Player(firstName = first, lastName = last, age = age, position = position, isGuest = guest)) }

    fun updatePlayer(player: Player, first: String, last: String, age: Int, position: String) =
        viewModelScope.launch { db.players().update(player.copy(firstName = first, lastName = last, age = age, position = position)) }

    fun setCollective(player: Player, inCollective: Boolean) =
        viewModelScope.launch { db.players().update(player.copy(isGuest = !inCollective)) }

    fun removePlayer(player: Player) = viewModelScope.launch { db.players().delete(player) }

    fun addEvent(title: String, date: LocalDate, type: EventType, recurrenceDays: Set<Int>, recurrenceEnd: LocalDate?) =
        viewModelScope.launch {
            val recurrence = recurrenceDays.sorted().joinToString(",")
            val end = recurrenceEnd ?: date
            var cursor = date
            while (!cursor.isAfter(end)) {
                if (cursor == date || cursor.dayOfWeek.value - 1 in recurrenceDays) {
                    db.events().insert(
                        VolleyEvent(
                            title = title,
                            type = type,
                            startsAt = cursor.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            durationMinutes = 120,
                            recurrenceDays = if (cursor == date) recurrence else "",
                            recurrenceEndAt = if (cursor == date) recurrenceEnd?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() else null
                        )
                    )
                }
                cursor = cursor.plusDays(1)
            }
        }

    fun cancel(event: VolleyEvent) = viewModelScope.launch { db.events().update(event.copy(cancelled = true)) }
    fun addGuest(eventId: Long, playerId: Long) = viewModelScope.launch { db.eventGuests().add(EventGuest(playerId, eventId)) }
    fun saveAttendance(playerId: Long, eventId: Long, status: AttendanceStatus) =
        viewModelScope.launch { db.attendance().save(Attendance(playerId, eventId, status)) }
}

@Composable
fun VolleyApp(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var palette by remember { mutableStateOf(AppPalette(Color(0xFF1565C0), Color(0xFF00897B), Color(0xFF6A1B9A))) }
    var showPalette by remember { mutableStateOf(false) }
    val players by vm.players.collectAsStateWithLifecycle(emptyList())
    val events by vm.events.collectAsStateWithLifecycle(emptyList())
    val attendance by vm.attendance.collectAsStateWithLifecycle(emptyList())
    val guests by vm.eventGuests.collectAsStateWithLifecycle(emptyList())
    MaterialTheme(colorScheme = lightColorScheme(primary = palette.primary, secondary = palette.secondary, tertiary = palette.tertiary)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Volley Manager", style = MaterialTheme.typography.titleLarge) },
                    actions = { IconButton(onClick = { showPalette = true }) { Icon(Icons.Default.Palette, "Personnaliser les couleurs") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("Tableau", "Joueurs", "Calendrier").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(if (index == 0) Icons.Default.Dashboard else if (index == 1) Icons.Default.Groups else Icons.Default.CalendarMonth, label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> Dashboard(players, events, attendance)
                    1 -> PlayersScreen(players, vm)
                    else -> CalendarArea(events, players, guests, attendance, vm)
                }
            }
            if (showPalette) {
                PaletteDialog(palette, { showPalette = false }) { palette = it; showPalette = false }
            }
        }
    }
}

@Composable
private fun PaletteDialog(current: AppPalette, onDismiss: () -> Unit, onSave: (AppPalette) -> Unit) {
        var primary by remember { mutableStateOf(current.primary) }
        var secondary by remember { mutableStateOf(current.secondary) }
        var tertiary by remember { mutableStateOf(current.tertiary) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Personnaliser l'apparence") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choisissez trois couleurs pour votre club.")
                    listOf("Principale" to primary, "Secondaire" to secondary, "Tertiaire" to tertiary).forEach { (label, selected) ->
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            paletteOptions.forEach { color ->
                                FilterChip(
                                    selected = selected == color,
                                    onClick = {
                                        when (label) {
                                            "Principale" -> primary = color
                                            "Secondaire" -> secondary = color
                                            else -> tertiary = color
                                        }
                                    },
                                    label = { Text("  ") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color,
                                        containerColor = color.copy(alpha = .18f)
                                    )
                                )
                        }
                    }
                }
            }
            },
            confirmButton = { Button(onClick = { onSave(AppPalette(primary, secondary, tertiary)) }) { Text("Appliquer") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
        )
}

@Composable
private fun Dashboard(players: List<Player>, events: List<VolleyEvent>, attendance: List<Attendance>) {
    val collective = players.filterNot { it.isGuest }
    val pastSessions = events.filter { it.type == EventType.TRAINING && !it.cancelled && eventDate(it).isBefore(LocalDate.now()) }
    val absenceCount = attendance.count { it.status == AttendanceStatus.ABSENT && it.playerId in collective.map { p -> p.id } }
    val expected = pastSessions.size * collective.size
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vue d'ensemble", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Collectif", collective.size.toString(), Modifier.weight(1f))
            MetricCard("Invités", players.count { it.isGuest }.toString(), Modifier.weight(1f))
        }
        MetricCard("Absence collectif", "${percent(absenceCount, expected)} %", Modifier.fillMaxWidth())
        Text("${pastSessions.size} séance(s) passée(s) depuis le début de la saison")
        Text("Les invités ne sont jamais inclus dans ces statistiques.")
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium); Text(label) } }
}

@Composable
private fun PlayersScreen(players: List<Player>, vm: MainViewModel) {
    var editing by remember { mutableStateOf<Player?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Joueurs", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { creating = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Ajouter") }
        }
        Text("Collectif", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        PlayerList(players.filterNot { it.isGuest }, vm) { editing = it }
        Text("Invités disponibles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        PlayerList(players.filter { it.isGuest }, vm) { editing = it }
    }
    if (creating) PlayerDialog(null, false, { creating = false }) { first, last, age, position, guest ->
        vm.addPlayer(first, last, age, position, guest)
        creating = false
    }
    editing?.let { player ->
        PlayerDialog(player, player.isGuest, { editing = null }) { first, last, age, position, _ ->
            vm.updatePlayer(player, first, last, age, position)
            editing = null
        }
    }
}

@Composable
private fun PlayerList(players: List<Player>, vm: MainViewModel, edit: (Player) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 220.dp)) {
        items(players) { player ->
            ListItem(
                headlineContent = { Text("${player.firstName} ${player.lastName}") },
                supportingContent = { Text("${player.position} · ${player.age} ans") },
                trailingContent = {
                    Row {
                        IconButton(onClick = { edit(player) }) { Icon(Icons.Default.Edit, "Modifier") }
                        IconButton(onClick = { vm.setCollective(player, player.isGuest) }) {
                            Icon(if (player.isGuest) Icons.Default.PersonAdd else Icons.Default.PersonRemove, "Modifier collectif")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PlayerDialog(player: Player?, guestDefault: Boolean, onDismiss: () -> Unit, onSave: (String, String, Int, String, Boolean) -> Unit) {
    var first by remember { mutableStateOf(player?.firstName.orEmpty()) }
    var last by remember { mutableStateOf(player?.lastName.orEmpty()) }
    var age by remember { mutableStateOf((player?.age ?: 18).toString()) }
    var position by remember { mutableStateOf(player?.position ?: positions.first()) }
    var guest by remember { mutableStateOf(guestDefault) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "Nouveau joueur" else "Modifier le joueur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("Prénom") })
                OutlinedTextField(last, { last = it }, label = { Text("Nom") })
                OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Âge") })
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(position, {}, readOnly = true, label = { Text("Poste") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        positions.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { position = choice; expanded = false }) }
                    }
                }
                Row { Checkbox(guest, { guest = it }); Text("Hors collectif / invité", Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(first, last, age.toInt(), position, guest) }, enabled = first.isNotBlank() && last.isNotBlank() && age.toIntOrNull() != null) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CalendarArea(
    events: List<VolleyEvent>,
    players: List<Player>,
    guests: List<EventGuest>,
    attendance: List<Attendance>,
    vm: MainViewModel
) {
    var subTab by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedEvent by remember { mutableStateOf<VolleyEvent?>(null) }
    var showAddForDate by remember { mutableStateOf<LocalDate?>(null) }
    Column(Modifier.padding(16.dp)) {
        PrimaryTabRow(selectedTabIndex = subTab) {
            listOf("Calendrier", "Présences", "Statistiques").forEachIndexed { index, label ->
                Tab(selected = subTab == index, onClick = { subTab = index }, text = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (subTab) {
            0 -> CalendarView(events, selectedDate, { date ->
                selectedDate = date
                if (events.any { !it.cancelled && eventDate(it) == date }) {
                    selectedEvent = events.firstOrNull { !it.cancelled && eventDate(it) == date }
                    subTab = 1
                } else {
                    showAddForDate = date
                }
            }, vm)
            1 -> AttendanceView(events, players, guests, attendance, selectedDate, selectedEvent, { selectedEvent = it }, vm)
            else -> StatisticsView(events, players, attendance)
        }
    }
    showAddForDate?.let { date ->
        EventDialog(initialDate = date, onDismiss = { showAddForDate = null }) { title, eventDate, type, recurrence, recurrenceEnd ->
            vm.addEvent(title, eventDate, type, recurrence, recurrenceEnd)
            showAddForDate = null
        }
    }
}

@Composable
private fun CalendarView(events: List<VolleyEvent>, selectedDate: LocalDate, onDate: (LocalDate) -> Unit, vm: MainViewModel) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var weekMode by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (weekMode) "Semaine du ${month.atDay(1).format(dateFormatter)}" else month.month.name.lowercase().replaceFirstChar(Char::uppercase) + " ${month.year}", style = MaterialTheme.typography.titleLarge)
            Row {
                FilterChip(selected = !weekMode, onClick = { weekMode = false }, label = { Text("Mois") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = weekMode, onClick = { weekMode = true }, label = { Text("Semaine") })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Précédent") }
            TextButton(onClick = { month = YearMonth.now(); onDate(LocalDate.now()) }) { Text("Aujourd'hui") }
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Suivant") }
        }
        if (weekMode) {
            val start = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            WeekRow(start, events, selectedDate, onDate)
        } else {
            Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
            val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (week in 0..5) {
                Row(Modifier.fillMaxWidth()) {
                    for (dayIndex in 0..6) {
                        val day = first.plusDays((week * 7 + dayIndex).toLong())
                        val dayEvents = events.filter { !it.cancelled && eventDate(it) == day }
                        TextButton(onClick = { onDate(day) }, modifier = Modifier.weight(1f)) {
                            Text(
                                if (day.month == month.month) "${day.dayOfMonth}${if (dayEvents.isNotEmpty()) " •" else ""}" else "",
                                color = dayEvents.firstOrNull()?.let(::eventColor) ?: LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter une séance ou un match") }
    }
    if (showAdd) EventDialog(onDismiss = { showAdd = false }) { title, date, type, recurrence, recurrenceEnd ->
        vm.addEvent(title, date, type, recurrence, recurrenceEnd)
        showAdd = false
    }
}

@Composable
private fun WeekRow(start: LocalDate, events: List<VolleyEvent>, selected: LocalDate, onDate: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        (0..6).forEach { offset ->
            val day = start.plusDays(offset.toLong())
            val dayEvents = events.filter { !it.cancelled && eventDate(it) == day }
            TextButton(onClick = { onDate(day) }, modifier = Modifier.weight(1f)) {
                Text(
                    "${weekdays[offset]}\n${day.dayOfMonth}${if (dayEvents.isNotEmpty()) " •" else ""}",
                    color = dayEvents.firstOrNull()?.let(::eventColor) ?: LocalContentColor.current
                )
            }
        }
    }
}

@Composable
private fun AttendanceView(
    events: List<VolleyEvent>,
    players: List<Player>,
    guests: List<EventGuest>,
    attendance: List<Attendance>,
    date: LocalDate,
    selectedEvent: VolleyEvent?,
    onEvent: (VolleyEvent?) -> Unit,
    vm: MainViewModel
) {
    val dayEvents = events.filter { !it.cancelled && eventDate(it) == date }
    Text("Séances du ${date.format(dateFormatter)}", style = MaterialTheme.typography.titleLarge)
    if (dayEvents.isEmpty()) Text("Aucune séance à cette date.", Modifier.padding(top = 16.dp))
    dayEvents.forEach { event ->
        OutlinedButton(onClick = { onEvent(event) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(event.title) }
    }
    selectedEvent?.let { event ->
        val guestIds = guests.filter { it.eventId == event.id }.map { it.playerId }.toSet()
        val roster = players.filter { !it.isGuest || it.id in guestIds }
        Text("Présences — ${event.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        roster.forEach { player ->
            val current = attendance.firstOrNull { it.playerId == player.id && it.eventId == event.id }?.status ?: if (player.isGuest) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${player.firstName} ${player.lastName}", Modifier.padding(top = 12.dp))
                AssistChip(
                    onClick = { vm.saveAttendance(player.id, event.id, if (current == AttendanceStatus.PRESENT) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT) },
                    label = { Text(if (current == AttendanceStatus.PRESENT) "Présent" else "Absent") }
                )
            }
        }
    }
}

@Composable
private fun StatisticsView(events: List<VolleyEvent>, players: List<Player>, attendance: List<Attendance>) {
    val collective = players.filterNot { it.isGuest }
    val sessions = events.filter { it.type == EventType.TRAINING && !it.cancelled && eventDate(it).isBefore(LocalDate.now()) }
    val currentMonth = YearMonth.now()
    val monthSessions = sessions.filter { YearMonth.from(eventDate(it)) == currentMonth }
    val seasonStart = sessions.minOfOrNull { eventDate(it) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Statistiques d'absence", style = MaterialTheme.typography.titleLarge)
        Text("Saison : ${seasonStart?.format(dateFormatter) ?: "aucune séance passée"} → aujourd'hui")
        Text("Séances passées : ${sessions.size} · ce mois-ci : ${monthSessions.size}")
        MetricCard("Absence du collectif — saison", "${collectiveAbsenceRate(collective, sessions, attendance)} %", Modifier.fillMaxWidth())
        MetricCard("Absence du collectif — mois", "${collectiveAbsenceRate(collective, monthSessions, attendance)} %", Modifier.fillMaxWidth())
        Text("Suivi individuel", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(collective) { player ->
                val season = playerAbsenceRate(player.id, sessions, attendance)
                val month = playerAbsenceRate(player.id, monthSessions, attendance)
                ListItem(
                    headlineContent = { Text("${player.firstName} ${player.lastName}") },
                    supportingContent = { Text("Saison : $season % · Mois : $month %") }
                )
            }
        }
        Text("Les invités sont exclus du suivi collectif et individuel.")
    }
}

@Composable
private fun EventDialog(
    initialDate: LocalDate = LocalDate.now(),
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, EventType, Set<Int>, LocalDate?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(initialDate.format(dateFormatter)) }
    var type by remember { mutableStateOf(EventType.TRAINING) }
    var recurring by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var endDateText by remember { mutableStateOf(initialDate.plusMonths(1).format(dateFormatter)) }
    val date = runCatching { LocalDate.parse(dateText, dateFormatter) }.getOrNull()
    val endDate = runCatching { LocalDate.parse(endDateText, dateFormatter) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel événement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nom") })
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Date obligatoire (AAAA-MM-JJ)") })
                Text("Type")
                Row { EventType.entries.forEach { eventType -> FilterChip(selected = type == eventType, onClick = { type = eventType }, label = { Text(eventType.label()) }); Spacer(Modifier.width(4.dp)) } }
                Row { Checkbox(recurring, { recurring = it }); Text("Séance récurrente", Modifier.padding(top = 12.dp)) }
                if (recurring) fullWeekdays.forEachIndexed { index, day ->
                    Row { Checkbox(index in selectedDays, { checked -> selectedDays = if (checked) selectedDays + index else selectedDays - index }); Text(day, Modifier.padding(top = 12.dp)) }
                }
                if (recurring) {
                    OutlinedTextField(endDateText, { endDateText = it }, label = { Text("Fin de récurrence (AAAA-MM-JJ)") })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, date!!, type, if (recurring) selectedDays else emptySet(), if (recurring) endDate else null) },
                enabled = title.isNotBlank() && date != null && (!recurring || (selectedDays.isNotEmpty() && endDate != null && !endDate.isBefore(date)))
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

private fun eventDate(event: VolleyEvent) = java.time.Instant.ofEpochMilli(event.startsAt).atZone(ZoneId.systemDefault()).toLocalDate()
private fun percent(value: Int, total: Int) = if (total == 0) 0 else value * 100 / total
private fun playerAbsenceRate(playerId: Long, sessions: List<VolleyEvent>, attendance: List<Attendance>) =
    percent(sessions.count { event -> attendance.any { it.playerId == playerId && it.eventId == event.id && it.status == AttendanceStatus.ABSENT } }, sessions.size)
private fun collectiveAbsenceRate(players: List<Player>, sessions: List<VolleyEvent>, attendance: List<Attendance>): Int {
    val total = players.size * sessions.size
    val absent = players.sumOf { player -> sessions.count { event -> attendance.any { it.playerId == player.id && it.eventId == event.id && it.status == AttendanceStatus.ABSENT } } }
    return percent(absent, total)
}
private fun EventType.label() = when (this) {
    EventType.TRAINING -> "Séance"
    EventType.MATCH -> "Match"
    EventType.EXCEPTIONAL -> "Exceptionnelle"
}
private fun recurrenceLabel(value: String) =
    if (value.isBlank()) "Pas de récurrence" else value.split(",").mapNotNull { it.toIntOrNull() }.joinToString(", ") { fullWeekdays[it] }

private fun eventColor(event: VolleyEvent) = when (event.type) {
    EventType.TRAINING -> Color(0xFF2E7D32)
    EventType.MATCH -> Color(0xFFC62828)
    EventType.EXCEPTIONAL -> Color(0xFF7B1FA2)
}
