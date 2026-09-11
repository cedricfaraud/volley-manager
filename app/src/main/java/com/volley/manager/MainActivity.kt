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
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

private val positions = listOf("Libéro", "Passeur", "Pointu", "Central", "R4")
private val weekdays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

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
        viewModelScope.launch {
            db.players().insert(Player(firstName = first, lastName = last, age = age, position = position, isGuest = guest))
        }

    fun updatePlayer(player: Player, first: String, last: String, age: Int, position: String) =
        viewModelScope.launch {
            db.players().update(player.copy(firstName = first, lastName = last, age = age, position = position))
        }

    fun setCollective(player: Player, inCollective: Boolean) =
        viewModelScope.launch { db.players().update(player.copy(isGuest = !inCollective)) }

    fun removePlayer(player: Player) = viewModelScope.launch { db.players().delete(player) }

    fun addEvent(title: String, date: String, type: EventType, recurrenceDays: Set<Int>) =
        viewModelScope.launch {
            val startsAt = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            db.events().insert(
                VolleyEvent(title = title, type = type, startsAt = startsAt, durationMinutes = 120,
                    recurrenceDays = recurrenceDays.sorted().joinToString(","))
            )
        }

    fun cancel(event: VolleyEvent) = viewModelScope.launch { db.events().update(event.copy(cancelled = true)) }
    fun addGuest(eventId: Long, playerId: Long) = viewModelScope.launch { db.eventGuests().add(EventGuest(playerId, eventId)) }
    fun removeGuest(eventId: Long, playerId: Long) = viewModelScope.launch { db.eventGuests().remove(EventGuest(playerId, eventId)) }
    fun saveAttendance(playerId: Long, eventId: Long, status: AttendanceStatus) =
        viewModelScope.launch { db.attendance().save(Attendance(playerId, eventId, status)) }

    fun addAbsence(playerId: Long, reason: String, days: Int) = viewModelScope.launch {
        val start = System.currentTimeMillis()
        db.absences().insert(Absence(playerId = playerId, startsAt = start, endsAt = start + days.coerceAtLeast(1) * 86_400_000L, reason = reason))
    }
}

@Composable
fun VolleyApp(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val players by vm.players.collectAsStateWithLifecycle(emptyList())
    val events by vm.events.collectAsStateWithLifecycle(emptyList())
    val attendance by vm.attendance.collectAsStateWithLifecycle(emptyList())
    val guests by vm.eventGuests.collectAsStateWithLifecycle(emptyList())
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1565C0))) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Volley Manager") }) },
            bottomBar = {
                NavigationBar {
                    listOf("Tableau", "Joueurs", "Calendrier", "Présences").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index },
                            icon = { Icon(if (index == 0) Icons.Default.Dashboard else if (index == 1) Icons.Default.Groups else if (index == 2) Icons.Default.CalendarMonth else Icons.Default.HowToReg, label) },
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
                    2 -> CalendarScreen(events, players, guests, vm)
                    else -> AttendanceScreen(players, events, guests, attendance, vm)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(players: List<Player>, events: List<VolleyEvent>, attendance: List<Attendance>) {
    val absences = attendance.count { it.status == AttendanceStatus.ABSENT || it.status == AttendanceStatus.EXCUSED }
    val rate = if (attendance.isEmpty()) 0 else absences * 100 / attendance.size
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vue d'ensemble", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Collectif", players.count { !it.isGuest }.toString(), Modifier.weight(1f))
            MetricCard("Invités", players.count { it.isGuest }.toString(), Modifier.weight(1f))
        }
        MetricCard("Taux d'absence", "$rate %", Modifier.fillMaxWidth())
        Text("${events.count { !it.cancelled }} événements planifiés")
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium); Text(label) } }
}

@Composable
private fun PlayersScreen(players: List<Player>, vm: MainViewModel) {
    var dialog by remember { mutableStateOf<Player?>(null) }
    var create by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Joueurs", style = MaterialTheme.typography.headlineSmall)
            Button({ create = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Ajouter") }
        }
        Text("Collectif", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        PlayerList(players.filterNot { it.isGuest }, vm, { dialog = it })
        Text("Invités disponibles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        PlayerList(players.filter { it.isGuest }, vm, { dialog = it })
    }
    if (create) PlayerDialog(null, false, { create = false }) { first, last, age, position, guest ->
        vm.addPlayer(first, last, age, position, guest); create = false
    }
    dialog?.let { player ->
        PlayerDialog(player, player.isGuest, { dialog = null }) { first, last, age, position, _ ->
            vm.updatePlayer(player, first, last, age, position); dialog = null
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
                        IconButton({ edit(player) }) { Icon(Icons.Default.Edit, "Modifier") }
                        IconButton({ vm.setCollective(player, player.isGuest) }) {
                            Icon(if (player.isGuest) Icons.Default.PersonAdd else Icons.Default.PersonRemove,
                                if (player.isGuest) "Ajouter au collectif" else "Retirer du collectif")
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (player == null) "Nouveau joueur" else "Modifier le joueur") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it }, label = { Text("Prénom") })
            OutlinedTextField(last, { last = it }, label = { Text("Nom") })
            OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Âge") })
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(position, {}, readOnly = true, label = { Text("Poste") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor())
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    positions.forEach { choice -> DropdownMenuItem({ Text(choice) }, { position = choice; expanded = false }) }
                }
            }
            Row { Checkbox(guest, { guest = it }); Text("Hors collectif / invité", Modifier.padding(top = 12.dp)) }
        }
    }, confirmButton = {
        Button(
            onClick = { onSave(first, last, age.toInt(), position, guest) },
            enabled = first.isNotBlank() && last.isNotBlank() && age.toIntOrNull() != null
        ) { Text("Enregistrer") }
    }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

@Composable
private fun CalendarScreen(events: List<VolleyEvent>, players: List<Player>, guests: List<EventGuest>, vm: MainViewModel) {
    var show by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("EEE d MMM", Locale.FRENCH) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Calendrier", style = MaterialTheme.typography.headlineSmall)
            Button({ show = true }) { Text("Ajouter") }
        }
        LazyColumn {
            items(events) { event ->
                val eventGuestIds = guests.filter { it.eventId == event.id }.map { it.playerId }.toSet()
                ListItem(
                    headlineContent = { Text("${event.title}${if (event.cancelled) " (ANNULÉE)" else ""}") },
                    supportingContent = { Text("${event.type.label()} · ${format.format(Date(event.startsAt))} · ${recurrenceLabel(event.recurrenceDays)}") },
                    trailingContent = {
                        Row {
                            if (!event.cancelled) IconButton({ vm.cancel(event) }) { Icon(Icons.Default.EventBusy, "Annuler") }
                            if (event.type == EventType.TRAINING) GuestPicker(players.filter { it.isGuest && it.id !in eventGuestIds }) { vm.addGuest(event.id, it.id) }
                        }
                    }
                )
            }
        }
    }
    if (show) EventDialog({ show = false }) { title, date, type, days -> vm.addEvent(title, date, type, days); show = false }
}

@Composable
private fun GuestPicker(available: List<Player>, onSelect: (Player) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }) { Icon(Icons.Default.PersonAdd, null); Text("Ajouter invité") }
        DropdownMenu(expanded, { expanded = false }) {
            available.forEach { player -> DropdownMenuItem({ Text("${player.firstName} ${player.lastName}") }, { onSelect(player); expanded = false }) }
            if (available.isEmpty()) DropdownMenuItem({ Text("Aucun invité disponible") }, { expanded = false })
        }
    }
}

@Composable
private fun EventDialog(onDismiss: () -> Unit, onSave: (String, String, EventType, Set<Int>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var type by remember { mutableStateOf(EventType.TRAINING) }
    var recurring by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nouvel événement") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Nom") })
            OutlinedTextField(date, { date = it }, label = { Text("Date obligatoire (AAAA-MM-JJ)") })
            Text("Type")
            Row {
                EventType.entries.forEach { eventType ->
                    FilterChip(
                        selected = type == eventType,
                        onClick = { type = eventType },
                        label = { Text(eventType.label()) }
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            Row { Checkbox(recurring, { recurring = it }); Text("Séance récurrente", Modifier.padding(top = 12.dp)) }
            if (recurring) {
                Text("Jours de récurrence")
                weekdays.forEachIndexed { index, day ->
                    Row {
                        Checkbox(index in selectedDays, { selectedDays = if (it) selectedDays + index else selectedDays - index })
                        Text(day, Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }, confirmButton = {
        Button(
            onClick = { onSave(title, date, type, if (recurring) selectedDays else emptySet()) },
            enabled = title.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess && (!recurring || selectedDays.isNotEmpty())
        ) { Text("Ajouter") }
    }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

@Composable
private fun AttendanceScreen(players: List<Player>, events: List<VolleyEvent>, guests: List<EventGuest>, attendance: List<Attendance>, vm: MainViewModel) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var absencePlayer by remember { mutableStateOf<Player?>(null) }
    val format = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }
    val selected = events.filter { format.format(Date(it.startsAt)) == date && !it.cancelled }
    Column(Modifier.padding(16.dp)) {
        Text("Présences à une date", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(date, { date = it }, label = { Text("Date (AAAA-MM-JJ)") })
        if (selected.isEmpty()) Text("Aucun événement à cette date.", Modifier.padding(top = 16.dp))
        selected.forEach { event ->
            val invitedIds = guests.filter { it.eventId == event.id }.map { it.playerId }
            val participants = players.filter { !it.isGuest || it.id in invitedIds }
            Text(event.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            participants.forEach { player ->
                val current = attendance.firstOrNull { it.playerId == player.id && it.eventId == event.id }?.status ?: AttendanceStatus.PRESENT
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${player.firstName} ${player.lastName}", Modifier.padding(top = 12.dp))
                    Row {
                        AssistChip(
                            onClick = {
                                vm.saveAttendance(
                                    player.id, event.id,
                                    if (current == AttendanceStatus.PRESENT) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
                                )
                            },
                            label = { Text(if (current == AttendanceStatus.PRESENT) "Présent" else "Absent") }
                        )
                        IconButton({ absencePlayer = player }) { Icon(Icons.Default.DateRange, "Absence sur une période") }
                    }
                }
            }
        }
    }
    absencePlayer?.let { player -> AbsenceDialog(player, { absencePlayer = null }) { reason, days -> vm.addAbsence(player.id, reason, days); absencePlayer = null } }
}

@Composable
private fun AbsenceDialog(player: Player, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var reason by remember { mutableStateOf("Blessure") }
    var days by remember { mutableStateOf("7") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Absence de ${player.firstName}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(reason, { reason = it }, label = { Text("Motif") })
            OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Durée en jours") })
        }
    }, confirmButton = {
        Button(onClick = { onSave(reason, days.toInt()) }, enabled = reason.isNotBlank() && days.toIntOrNull() != null) {
            Text("Enregistrer")
        }
    },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

private fun EventType.label() = when (this) {
    EventType.TRAINING -> "Séance"
    EventType.MATCH -> "Match"
    EventType.EXCEPTIONAL -> "Exceptionnelle"
}

private fun recurrenceLabel(value: String) =
    if (value.isBlank()) "Pas de récurrence" else value.split(",").mapNotNull { it.toIntOrNull() }.joinToString(", ") { weekdays[it] }
