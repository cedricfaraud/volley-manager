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

    fun addPlayer(first: String, last: String, age: Int, position: String, guest: Boolean) =
        viewModelScope.launch { db.players().insert(Player(firstName = first, lastName = last, age = age, position = position, isGuest = guest)) }

    fun removePlayer(player: Player) = viewModelScope.launch { db.players().delete(player) }

    fun addEvent(title: String, date: String, type: EventType, recurrence: String) = viewModelScope.launch {
        val startsAt = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.events().insert(
            VolleyEvent(title = title, type = type, startsAt = startsAt,
                durationMinutes = 120, recurrence = recurrence)
        )
    }

    fun cancel(event: VolleyEvent) = viewModelScope.launch {
        db.events().update(event.copy(cancelled = true))
    }

    fun saveAttendance(playerId: Long, eventId: Long, status: AttendanceStatus) =
        viewModelScope.launch { db.attendance().save(Attendance(playerId, eventId, status)) }
}

@Composable
fun VolleyApp(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val players by vm.players.collectAsStateWithLifecycle(emptyList())
    val events by vm.events.collectAsStateWithLifecycle(emptyList())
    val attendance by vm.attendance.collectAsStateWithLifecycle(emptyList())
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1565C0))) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Volley Manager") }) },
            bottomBar = {
                NavigationBar {
                    listOf("Tableau", "Joueurs", "Calendrier", "Présences").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                Icon(
                                    when (i) {
                                        0 -> Icons.Default.Dashboard
                                        1 -> Icons.Default.Groups
                                        2 -> Icons.Default.CalendarMonth
                                        else -> Icons.Default.HowToReg
                                    }, label
                                )
                            },
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
                    2 -> CalendarScreen(events, vm)
                    else -> AttendanceScreen(players, events, attendance, vm)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(players: List<Player>, events: List<VolleyEvent>, attendance: List<Attendance>) {
    val total = attendance.size
    val absent = attendance.count { it.status == AttendanceStatus.ABSENT || it.status == AttendanceStatus.EXCUSED }
    val rate = if (total == 0) 0 else absent * 100 / total
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vue d'ensemble", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Joueurs", players.size.toString(), Modifier.weight(1f))
            MetricCard("Événements", events.size.toString(), Modifier.weight(1f))
        }
        MetricCard("Taux d'absence", "$rate %", Modifier.fillMaxWidth())
        Text("Suivi mensuel et saison", style = MaterialTheme.typography.titleLarge)
        Text("Les statistiques se basent sur les présences saisies. Les séances annulées ne sont pas comptabilisées.")
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label)
        }
    }
}

@Composable
private fun PlayersScreen(players: List<Player>, vm: MainViewModel) {
    var show by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mon collectif", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { show = true }) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(6.dp))
                Text("Ajouter")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(players) { player ->
                ListItem(
                    headlineContent = { Text("${player.firstName} ${player.lastName}") },
                    supportingContent = { Text("${player.position} · ${player.age} ans${if (player.isGuest) " · Invité" else ""}") },
                    trailingContent = {
                        IconButton(onClick = { vm.removePlayer(player) }) {
                            Icon(Icons.Default.Delete, "Supprimer")
                        }
                    }
                )
            }
        }
    }
    if (show) {
        PlayerDialog(onDismiss = { show = false }) { first, last, age, position, guest ->
            vm.addPlayer(first, last, age, position, guest)
            show = false
        }
    }
}

@Composable
private fun PlayerDialog(onDismiss: () -> Unit, onSave: (String, String, Int, String, Boolean) -> Unit) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("18") }
    var position by remember { mutableStateOf("R4") }
    var guest by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau joueur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("Prénom") })
                OutlinedTextField(last, { last = it }, label = { Text("Nom") })
                OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Âge") })
                OutlinedTextField(position, { position = it }, label = { Text("Poste") })
                Row {
                    Checkbox(guest, { guest = it })
                    Text("Invité", Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = first.isNotBlank() && last.isNotBlank(),
                onClick = { onSave(first, last, age.toIntOrNull() ?: 0, position, guest) }
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CalendarScreen(events: List<VolleyEvent>, vm: MainViewModel) {
    var show by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRENCH) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Calendrier", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { show = true }) { Text("Ajouter") }
        }
        LazyColumn {
            items(events) { event ->
                ListItem(
                    headlineContent = { Text("${event.title}${if (event.cancelled) " (ANNULÉE)" else ""}") },
                    supportingContent = {
                        Text("${event.type.name.lowercase().replaceFirstChar(Char::uppercase)} · ${format.format(Date(event.startsAt))} · ${event.recurrence}")
                    },
                    trailingContent = {
                        if (!event.cancelled) {
                            IconButton(onClick = { vm.cancel(event) }) {
                                Icon(Icons.Default.EventBusy, "Annuler")
                            }
                        }
                    }
                )
            }
        }
    }
    if (show) {
        EventDialog(onDismiss = { show = false }) { title, date, type, recurrence ->
            vm.addEvent(title, date, type, recurrence)
            show = false
        }
    }
}

@Composable
private fun EventDialog(onDismiss: () -> Unit, onSave: (String, String, EventType, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("Aucune") }
    var type by remember { mutableStateOf(EventType.TRAINING) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel événement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nom") })
                OutlinedTextField(date, { date = it }, label = { Text("Date obligatoire (AAAA-MM-JJ)") })
                Text("Type")
                Row {
                    EventType.entries.forEach { eventType ->
                        FilterChip(
                            selected = type == eventType,
                            onClick = { type = eventType },
                            label = { Text(eventType.name) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
                OutlinedTextField(recurrence, { recurrence = it }, label = { Text("Récurrence (ex. chaque mardi)") })
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess,
                onClick = { onSave(title, date, type, recurrence) }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun AttendanceScreen(
    players: List<Player>,
    events: List<VolleyEvent>,
    attendance: List<Attendance>,
    vm: MainViewModel
) {
    Column(Modifier.padding(16.dp)) {
        Text("Suivi des présences", style = MaterialTheme.typography.headlineSmall)
        if (events.isEmpty()) Text("Ajoutez une séance dans le calendrier pour commencer.")
        events.filterNot { it.cancelled }.forEach { event ->
            Text(event.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            players.forEach { player ->
                val current = attendance.firstOrNull { it.playerId == player.id && it.eventId == event.id }?.status
                    ?: AttendanceStatus.PRESENT
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${player.firstName} ${player.lastName}", Modifier.padding(top = 12.dp))
                    AssistChip(
                        onClick = {
                            vm.saveAttendance(
                                player.id, event.id,
                                if (current == AttendanceStatus.PRESENT) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
                            )
                        },
                        label = { Text(if (current == AttendanceStatus.PRESENT) "Présent" else "Absent") }
                    )
                }
            }
        }
    }
}
