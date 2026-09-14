@file:OptIn(ExperimentalMaterial3Api::class)

package com.volley.manager

import android.os.Bundle
import android.content.Intent
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.volley.manager.data.*
import com.volley.manager.domain.absenceRate
import com.volley.manager.domain.absenceBreakdown
import com.volley.manager.domain.collectiveAbsenceBreakdown
import com.volley.manager.domain.collectiveAbsenceRate
import com.volley.manager.domain.percentage
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val positions = listOf("Libéro", "Passeur", "Pointu", "Central", "R4")
private val weekdays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
private val fullWeekdays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun defaultRecurrenceEnd(date: LocalDate): LocalDate {
    var result = LocalDate.of(date.year + 1, 6, 30)
    while (result.dayOfWeek != date.dayOfWeek) result = result.minusDays(1)
    return result
}

private fun capitalizeName(value: String): String =
    value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        .joinToString(" ") { part -> part.lowercase(Locale.getDefault()).replaceFirstChar(Char::uppercaseChar) }

private fun isValidPhone(value: String): Boolean =
    value.isBlank() || value.matches(Regex("^\\+?[0-9][0-9 .()-]{7,}$"))

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
    val feedback = db.feedback().observeAll()

    fun addPlayer(first: String, last: String, age: Int, position: String, guest: Boolean, email: String, phone: String, heightCm: Int?, jerseyNumber: Int?, notes: String, onCreated: (Long) -> Unit = {}) =
        viewModelScope.launch {
            val id = db.players().insert(Player(firstName = first, lastName = last, age = age, position = position, isGuest = guest, email = email, phone = phone, heightCm = heightCm, jerseyNumber = jerseyNumber, notes = notes))
            onCreated(id)
        }

    fun updatePlayer(player: Player, first: String, last: String, age: Int, position: String, guest: Boolean, email: String, phone: String, heightCm: Int?, jerseyNumber: Int?, notes: String) =
        viewModelScope.launch { db.players().update(player.copy(firstName = first, lastName = last, age = age, position = position, isGuest = guest, email = email, phone = phone, heightCm = heightCm, jerseyNumber = jerseyNumber, notes = notes)) }

    fun updateRatings(player: Player, ratings: List<Int>) =
        viewModelScope.launch {
            db.players().update(player.copy(
                serviceRating = ratings[0], receptionRating = ratings[1], settingRating = ratings[2],
                attackRating = ratings[3], blockRating = ratings[4], defenseRating = ratings[5],
                motivationRating = ratings[6], techniqueRating = ratings[7]
            ))
        }

    fun setCollective(player: Player, inCollective: Boolean) =
        viewModelScope.launch { db.players().update(player.copy(isGuest = !inCollective)) }

    fun removePlayer(player: Player) = viewModelScope.launch { db.players().delete(player) }

    fun addEvent(title: String, date: LocalDate, type: EventType, recurrenceDays: Set<Int>, recurrenceEnd: LocalDate?) =
        viewModelScope.launch {
            val recurrence = recurrenceDays.sorted().joinToString(",")
            val end = recurrenceEnd ?: date.plusYears(1)
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

    fun applyAbsencePeriod(playerId: Long, events: List<VolleyEvent>, start: LocalDate, end: LocalDate, status: AttendanceStatus, reason: String) =
        viewModelScope.launch {
            events.filter { !it.cancelled && eventDate(it) in start..end }
                .forEach { db.attendance().save(Attendance(playerId, it.id, status)) }
            db.absences().insert(
                Absence(
                    playerId = playerId,
                    startsAt = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endsAt = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1,
                    reason = reason
                )
            )
        }

    fun clearFutureAbsences(playerId: Long, events: List<VolleyEvent>, from: LocalDate) =
        viewModelScope.launch {
            events.filter { !it.cancelled && eventDate(it).isAfter(from) }
                .forEach { db.attendance().save(Attendance(playerId, it.id, AttendanceStatus.PRESENT)) }
        }

    fun addFeedback(category: String, title: String, details: String) =
        viewModelScope.launch { db.feedback().insert(Feedback(category = category, title = title, details = details)) }
}

@Composable
fun VolleyApp(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var palette by remember { mutableStateOf(AppPalette(Color(0xFF1565C0), Color(0xFF00897B), Color(0xFF6A1B9A))) }
    var showPalette by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    val players by vm.players.collectAsStateWithLifecycle(emptyList())
    val events by vm.events.collectAsStateWithLifecycle(emptyList())
    val attendance by vm.attendance.collectAsStateWithLifecycle(emptyList())
    val guests by vm.eventGuests.collectAsStateWithLifecycle(emptyList())
    val feedback by vm.feedback.collectAsStateWithLifecycle(emptyList())
    val appColors = lightColorScheme(
        primary = palette.tertiary,
        onPrimary = Color.White,
        secondary = palette.secondary,
        onSecondary = Color.White,
        tertiary = palette.tertiary,
        background = palette.primary.copy(alpha = .07f),
        onBackground = Color(0xFF17202A),
        surface = Color.White,
        onSurface = Color(0xFF17202A),
        surfaceVariant = palette.primary.copy(alpha = .10f),
        onSurfaceVariant = Color(0xFF52606D)
    )
    MaterialTheme(colorScheme = appColors, typography = MaterialTheme.typography.copy(
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    )) {
        Scaffold(
            containerColor = appColors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Volley Manager", style = MaterialTheme.typography.titleLarge)
                            Text("Piloter le collectif", style = MaterialTheme.typography.labelMedium, color = appColors.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFeedback = true }) { Icon(Icons.Default.Feedback, "Journal de feedback") }
                        IconButton(onClick = { showPalette = true }) { Icon(Icons.Default.Palette, "Personnaliser les couleurs") }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
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
            if (showFeedback) {
                FeedbackDialog(feedback, { showFeedback = false }) { category, title, details ->
                    vm.addFeedback(category, title, details)
                }
            }
        }

    }
}

@Composable
private fun FeedbackDialog(feedback: List<Feedback>, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var category by remember { mutableStateOf("Bug") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var showJournal by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val export = feedback.joinToString("\n\n") { "# ${it.category} — ${it.title}\n${it.details}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Journal de feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Les retours sont conservés localement pour les traiter ensuite comme des Issues GitHub.")
                Row {
                    listOf("Bug", "Idée", "Amélioration").forEach { option ->
                        FilterChip(category == option, { category = option }, label = { Text(option) })
                        Spacer(Modifier.width(4.dp))
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Titre") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(details, { details = it }, label = { Text("Description") }, minLines = 3, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                TextButton(onClick = { showJournal = !showJournal }) { Text(if (showJournal) "Masquer l'historique" else "Voir l'historique (${feedback.size})") }
                if (showJournal) {
                    feedback.take(5).forEach { Text("${it.category} — ${it.title}", style = MaterialTheme.typography.bodySmall) }
                    if (feedback.isNotEmpty()) TextButton(onClick = { clipboard.setText(AnnotatedString(export)) }) { Text("Copier pour GitHub Issues") }
                }
                TextButton(
                    enabled = title.isNotBlank() && details.isNotBlank(),
                    onClick = {
                        val issueUrl = "https://github.com/cedricfaraud/volley-manager/issues/new" +
                            "?title=${android.net.Uri.encode("$category — $title")}" +
                            "&body=${android.net.Uri.encode(details)}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(issueUrl)))
                    }
                ) { Text("Créer une Issue GitHub") }
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank() && details.isNotBlank(), onClick = { onSave(category, title, details); onDismiss() }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Vue d'ensemble", style = MaterialTheme.typography.headlineMedium)
        Text("Les chiffres clés de votre saison", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Collectif", collective.size.toString(), Icons.Default.Groups, Modifier.weight(1f))
            MetricCard("Invités", players.count { it.isGuest }.toString(), Icons.Default.PersonAdd, Modifier.weight(1f))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .14f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Forme du collectif", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text("${collectiveAbsenceRate(collective.map { it.id }, pastSessions, attendance)} %", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.secondary)
                Text("taux d'absence · ${pastSessions.size} séance(s) passée(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Les invités ne sont jamais inclus dans ces statistiques.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .14f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .16f), CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(19.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayersScreen(players: List<Player>, vm: MainViewModel) {
    var editing by remember { mutableStateOf<Player?>(null) }
    var ratingPlayer by remember { mutableStateOf<Player?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Joueurs", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { creating = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Ajouter") }
        }
        Column(Modifier.weight(0.7f).fillMaxWidth().padding(top = 12.dp)) {
            Text("Collectif", style = MaterialTheme.typography.titleMedium)
            PlayerList(players.filterNot { it.isGuest }, vm, { editing = it }, { ratingPlayer = it }, Modifier.weight(1f))
        }
        Column(Modifier.weight(0.3f).fillMaxWidth().padding(top = 12.dp)) {
            Text("Invités disponibles", style = MaterialTheme.typography.titleMedium)
            PlayerList(players.filter { it.isGuest }, vm, { editing = it }, { ratingPlayer = it }, Modifier.weight(1f))
        }
    }
    if (creating) PlayerDialog(null, false, { creating = false }) { first, last, age, position, guest, email, phone, heightCm, jerseyNumber, notes ->
        vm.addPlayer(first, last, age, position, guest, email, phone, heightCm, jerseyNumber, notes)
        creating = false
    }
    editing?.let { player ->
    PlayerDialog(player, player.isGuest, { editing = null }) { first, last, age, position, guest, email, phone, heightCm, jerseyNumber, notes ->
        vm.updatePlayer(player, first, last, age, position, guest, email, phone, heightCm, jerseyNumber, notes)
            editing = null
        }
    }
    ratingPlayer?.let { player ->
        RatingsDialog(player, { ratingPlayer = null }) { ratings ->
            vm.updateRatings(player, ratings)
            ratingPlayer = null
        }
    }
}

@Composable
private fun PlayerList(players: List<Player>, vm: MainViewModel, edit: (Player) -> Unit, rate: (Player) -> Unit, modifier: Modifier = Modifier) {
    val sortedPlayers = players.sortedWith(
        compareBy<Player> { positions.indexOf(it.position).takeIf { index -> index >= 0 } ?: positions.size }
            .thenComparator { first, second ->
                val firstScore = weightedRating(
                    first.position,
                    listOf(first.serviceRating, first.receptionRating, first.settingRating, first.attackRating, first.blockRating, first.defenseRating, first.motivationRating, first.techniqueRating)
                )
                val secondScore = weightedRating(
                    second.position,
                    listOf(second.serviceRating, second.receptionRating, second.settingRating, second.attackRating, second.blockRating, second.defenseRating, second.motivationRating, second.techniqueRating)
                )
                when {
                    firstScore == 0.0 && secondScore != 0.0 -> 1
                    firstScore != 0.0 && secondScore == 0.0 -> -1
                    else -> secondScore.compareTo(firstScore)
                }
            }
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        items(sortedPlayers) { player ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { rate(player) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .08f), RoundedCornerShape(16.dp)),
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

private val ratingLabels = listOf("Service", "Réception", "Passe", "Attaque", "Bloc", "Défense", "Envie", "Technique")

private fun weightedRating(position: String, ratings: List<Int>): Double {
    val coefficients = when (position) {
        "Passeur" -> listOf(4, 0, 10, 0, 2, 1, 1, 1)
        "R4" -> listOf(4, 10, 0, 8, 1, 4, 1, 1)
        "Central" -> listOf(4, 0, 0, 6, 10, 0, 1, 0)
        "Pointu" -> listOf(4, 0, 0, 10, 4, 3, 1, 1)
        "Libéro" -> listOf(0, 10, 1, 0, 0, 10, 2, 1)
        else -> List(8) { 1 }
    }
    val total = coefficients.sum()
    return if (total == 0) 0.0 else ratings.zip(coefficients).sumOf { (rating, coefficient) -> rating * coefficient }.toDouble() / total
}

@Composable
private fun RatingsDialog(player: Player, onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    val initial = listOf(player.serviceRating, player.receptionRating, player.settingRating, player.attackRating, player.blockRating, player.defenseRating, player.motivationRating, player.techniqueRating)
    var ratings by remember { mutableStateOf(initial) }
    val otherPositions = positions.filter { it != player.position }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Évaluation de ${player.firstName} ${player.lastName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${player.position} · ${"%.1f".format(Locale.getDefault(), weightedRating(player.position, ratings))}/20",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    otherPositions.forEach { position ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(54.dp)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = .16f), CircleShape),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    "%.1f".format(Locale.getDefault(), weightedRating(position, ratings)),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(position, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                ratingLabels.forEachIndexed { index, label ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label)
                            Text("${ratings[index]}/20", color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = ratings[index].toFloat(),
                            onValueChange = { value ->
                                ratings = ratings.toMutableList().also { it[index] = value.roundToInt().coerceIn(0, 20) }
                            },
                            valueRange = 0f..20f,
                            steps = 19
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(ratings) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PlayerDialog(player: Player?, guestDefault: Boolean, onDismiss: () -> Unit, onSave: (String, String, Int, String, Boolean, String, String, Int?, Int?, String) -> Unit) {
    var first by remember { mutableStateOf(player?.firstName.orEmpty()) }
    var last by remember { mutableStateOf(player?.lastName.orEmpty()) }
    var age by remember { mutableStateOf((player?.age ?: 18).toString()) }
    var position by remember { mutableStateOf(player?.position ?: positions.first()) }
    var guest by remember { mutableStateOf(guestDefault) }
    var expanded by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(player?.email.orEmpty()) }
    var phone by remember { mutableStateOf(player?.phone.orEmpty()) }
    var height by remember { mutableStateOf(player?.heightCm?.toString().orEmpty()) }
    var jerseyNumber by remember { mutableStateOf(player?.jerseyNumber?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(player?.notes.orEmpty()) }
    val emailValid = email.isBlank() || Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val phoneValid = isValidPhone(phone.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "Nouveau joueur" else "Modifier le joueur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("Prénom") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(last, { last = it }, label = { Text("Nom") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Âge") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(position, {}, readOnly = true, label = { Text("Poste") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        positions.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { position = choice; expanded = false }) }
                    }
                }
                Row { Checkbox(guest, { guest = it }); Text("Hors collectif / invité", Modifier.padding(top = 12.dp)) }
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Masquer les informations détaillées" else "Ajouter des informations détaillées")
                }
                if (showDetails) {
                    OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, isError = !emailValid, supportingText = { if (!emailValid) Text("Format d'e-mail invalide") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(phone, { phone = it }, label = { Text("Téléphone") }, isError = !phoneValid, supportingText = { if (!phoneValid) Text("Format de téléphone invalide") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(height, { height = it.filter(Char::isDigit) }, label = { Text("Taille (cm)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(jerseyNumber, { jerseyNumber = it.filter(Char::isDigit) }, label = { Text("N° maillot") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(capitalizeName(first), capitalizeName(last), age.toInt(), position, guest, email.trim(), phone.trim(), height.toIntOrNull(), jerseyNumber.toIntOrNull(), notes.trim())
            }, enabled = first.isNotBlank() && last.isNotBlank() && age.toIntOrNull() != null && emailValid && phoneValid) { Text("Enregistrer") }
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
            1 -> AttendanceView(
                events, players, guests, attendance, selectedDate, selectedEvent,
                { event ->
                    selectedEvent = event
                    if (event != null) selectedDate = eventDate(event)
                },
                vm
            )
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                weekdays.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (week in 0..5) {
                Row(Modifier.fillMaxWidth()) {
                    for (dayIndex in 0..6) {
                        val day = first.plusDays((week * 7 + dayIndex).toLong())
                        val inMonth = day.month == month.month
                        val dayEvents = if (inMonth) {
                            events.filter { !it.cancelled && eventDate(it) == day }
                        } else {
                            emptyList()
                        }
                        val selected = day == selectedDate
                        val eventTint = dayEvents.firstOrNull()?.let(::eventColor)
                        Box(Modifier.weight(1f).padding(2.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            TextButton(
                                onClick = { onDate(day) },
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = when {
                                        selected -> MaterialTheme.colorScheme.tertiary
                                        dayEvents.isNotEmpty() -> (eventTint ?: MaterialTheme.colorScheme.secondary).copy(alpha = .12f)
                                        else -> Color.Transparent
                                    }
                                )
                            ) {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(
                                        if (inMonth) day.dayOfMonth.toString() else "",
                                        color = if (selected) Color.White else eventTint ?: MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (dayEvents.isNotEmpty()) {
                                        Box(Modifier.size(4.dp).background(if (selected) Color.White else eventTint ?: MaterialTheme.colorScheme.tertiary, CircleShape))
                                    }
                                }
                            }
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
    val orderedEvents = events.filterNot { it.cancelled }.sortedBy { it.startsAt }
    val dayEvents = orderedEvents.filter { eventDate(it) == date }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Séances du ${date.format(dateFormatter)}", style = MaterialTheme.typography.titleLarge)
    if (dayEvents.isEmpty()) Text("Aucune séance à cette date.", Modifier.padding(top = 16.dp))
    if (selectedEvent == null) {
        dayEvents.forEach { event ->
            TextButton(onClick = { onEvent(event) }, modifier = Modifier.fillMaxWidth()) { Text(event.title) }
        }
    }
    selectedEvent?.let { event ->
        val eventIndex = orderedEvents.indexOfFirst { it.id == event.id }
        val previous = orderedEvents.getOrNull(eventIndex - 1)
        val next = orderedEvents.getOrNull(eventIndex + 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (previous != null) {
                TextButton(onClick = { onEvent(previous) }) {
                    Icon(Icons.Default.ChevronLeft, "Événement précédent")
                    Text("Précédent")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (next != null) {
                TextButton(onClick = { onEvent(next) }) {
                    Text("Suivant")
                    Icon(Icons.Default.ChevronRight, "Événement suivant")
                }
            }
        }
        val guestIds = guests.filter { it.eventId == event.id }.map { it.playerId }.toSet()
        val roster = players.filter { !it.isGuest || it.id in guestIds }
        val presentCount = roster.count { player ->
            val status = attendance.firstOrNull { record ->
                record.playerId == player.id && record.eventId == event.id
            }?.status ?: if (player.isGuest) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
            status == AttendanceStatus.PRESENT
        }
        Text("Présences — ${event.title} ($presentCount/${roster.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        var showGuestPicker by remember(event.id) { mutableStateOf(false) }
        var showGuestCreation by remember(event.id) { mutableStateOf(false) }
        var periodPlayer by remember(event.id) { mutableStateOf<Player?>(null) }
        var pendingPresence by remember(event.id) { mutableStateOf<Player?>(null) }
        OutlinedButton(onClick = { showGuestPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PersonAdd, null)
            Spacer(Modifier.width(6.dp))
            Text("Ajouter un invité")
        }
        roster.forEach { player ->
            val current = attendance.firstOrNull { it.playerId == player.id && it.eventId == event.id }?.status ?: if (player.isGuest) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${player.firstName} ${player.lastName}", Modifier.padding(top = 12.dp))
                AssistChip(
                    modifier = Modifier.pointerInput(event.id, player.id, current) {
                        detectTapGestures(
                            onLongPress = { if (current != AttendanceStatus.PRESENT) periodPlayer = player },
                            onTap = {
                                val next = when (current) {
                                    AttendanceStatus.PRESENT -> AttendanceStatus.ABSENT
                                    AttendanceStatus.ABSENT -> AttendanceStatus.EXCUSED
                                    else -> AttendanceStatus.PRESENT
                                }
                                if (next == AttendanceStatus.PRESENT && events.any {
                                        !it.cancelled && eventDate(it).isAfter(eventDate(event)) &&
                                            attendance.any { record ->
                                                record.playerId == player.id &&
                                                    record.eventId == it.id &&
                                                    (record.status == AttendanceStatus.ABSENT || record.status == AttendanceStatus.EXCUSED)
                                            }
                                    }) {
                                    pendingPresence = player
                                } else {
                                    vm.saveAttendance(player.id, event.id, next)
                                }
                            }
                        )
                    },
                    onClick = {},
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (current) {
                            AttendanceStatus.PRESENT -> Color(0xFFDDF5E3)
                            AttendanceStatus.EXCUSED -> Color(0xFFFFE8C2)
                            else -> Color(0xFFFFDAD6)
                        },
                        labelColor = when (current) {
                            AttendanceStatus.PRESENT -> Color(0xFF176B32)
                            AttendanceStatus.EXCUSED -> Color(0xFF8A4B00)
                            else -> Color(0xFFB3261E)
                        }
                    ),
                    label = {
                        Text(
                            when (current) {
                                AttendanceStatus.PRESENT -> "Présent"
                                AttendanceStatus.EXCUSED -> "Absent justifié"
                                else -> "Absent"
                            }
                        )
                    }
                )
            }
        }
        if (showGuestPicker) {
            AlertDialog(
                onDismissRequest = { showGuestPicker = false },
                title = { Text("Ajouter un invité") },
                text = {
                    Column {
                        TextButton(onClick = { showGuestCreation = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PersonAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Créer un invité")
                        }
                        players.filter { it.isGuest && it.id !in guestIds }.forEach { guest ->
                            TextButton(
                                onClick = { vm.addGuest(event.id, guest.id); showGuestPicker = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("${guest.firstName} ${guest.lastName}") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showGuestPicker = false }) { Text("Fermer") } }
            )
        }
        if (showGuestCreation) {
            PlayerDialog(null, true, { showGuestCreation = false }) { first, last, age, position, _, email, phone, heightCm, jerseyNumber, notes ->
                vm.addPlayer(first, last, age, position, true, email, phone, heightCm, jerseyNumber, notes) { playerId ->
                    vm.addGuest(event.id, playerId)
                }
                showGuestCreation = false
            }
        }
        periodPlayer?.let { player ->
            AbsencePeriodDialog(
                initialDate = eventDate(event),
                onDismiss = { periodPlayer = null },
                onSave = { endDate, status, reason ->
                    vm.applyAbsencePeriod(player.id, events, eventDate(event), endDate, status, reason)
                    periodPlayer = null
                }
            )
        }
        pendingPresence?.let { player ->
            AlertDialog(
                onDismissRequest = { pendingPresence = null },
                title = { Text("Absence planifiée") },
                text = { Text("Ce joueur a d'autres absences prévues. Les passer aussi en présence ?") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.clearFutureAbsences(player.id, events, eventDate(event))
                        vm.saveAttendance(player.id, event.id, AttendanceStatus.PRESENT)
                        pendingPresence = null
                    }) { Text("Oui, toutes") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        vm.saveAttendance(player.id, event.id, AttendanceStatus.PRESENT)
                        pendingPresence = null
                    }) { Text("Seulement celle-ci") }
                }
            )
        }
    }
    }
}

@Composable
private fun AbsencePeriodDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate, AttendanceStatus, String) -> Unit
) {
    var endDate by remember { mutableStateOf(initialDate) }
    var justified by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Absence sur une période") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Début : ${initialDate.format(dateFormatter)}")
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("Fin : ${endDate.format(dateFormatter)}")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = justified, onCheckedChange = { justified = it })
                    Text("Justifié")
                }
                if (justified) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motif") },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(endDate, if (justified) AttendanceStatus.EXCUSED else AttendanceStatus.ABSENT, reason.trim())
                }
            ) { Text("Appliquer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        endDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = state) }
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
        val collectiveSeason = collectiveAbsenceBreakdown(collective.map { it.id }, sessions, attendance)
        val collectiveMonth = collectiveAbsenceBreakdown(collective.map { it.id }, monthSessions, attendance)
        MetricCard("Absence du collectif — saison", "${collectiveSeason.totalRate} % dont ${collectiveSeason.justifiedRate} % justifiées", Icons.Default.Insights, Modifier.fillMaxWidth())
        MetricCard("Absence du collectif — mois", "${collectiveMonth.totalRate} % dont ${collectiveMonth.justifiedRate} % justifiées", Icons.Default.CalendarMonth, Modifier.fillMaxWidth())
        Text("Suivi individuel", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(collective.sortedByDescending { absenceRate(it.id, sessions, attendance) }) { player ->
                val season = absenceBreakdown(player.id, sessions, attendance)
                val month = absenceBreakdown(player.id, monthSessions, attendance)
                ListItem(
                    headlineContent = { Text("${player.firstName} ${player.lastName}") },
                    supportingContent = {
                        Text(
                            "Mois : ${month.totalRate} % dont ${month.justifiedRate} % justifiées · " +
                                "Année : ${season.totalRate} % dont ${season.justifiedRate} % justifiées"
                        )
                    }
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
    var endDate by remember { mutableStateOf<LocalDate?>(defaultRecurrenceEnd(initialDate)) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateText, dateFormatter) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel événement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nom") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Date obligatoire (AAAA-MM-JJ)") })
                Text("Type")
                Row { EventType.entries.forEach { eventType -> FilterChip(selected = type == eventType, onClick = { type = eventType }, label = { Text(eventType.label()) }); Spacer(Modifier.width(4.dp)) } }
                Row { Checkbox(recurring, { recurring = it }); Text("Séance récurrente", Modifier.padding(top = 12.dp)) }
                if (recurring) {
                    Text(
                        "Séance récurrente tous les ${date?.let { fullWeekdays[it.dayOfWeek.value - 1] } ?: "..."}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(endDate != null, { checked ->
                            endDate = if (checked) defaultRecurrenceEnd(date ?: initialDate) else null
                        })
                        Text("Définir une date de fin (facultatif)")
                    }
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        enabled = endDate != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(endDate?.format(dateFormatter) ?: "Aucune date de fin")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, date!!, type, if (recurring) setOf(date.dayOfWeek.value - 1) else emptySet(), if (recurring) endDate else null) },
                enabled = title.isNotBlank() && date != null && (!recurring || endDate == null || !endDate!!.isBefore(date))
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
    if (showEndDatePicker) {
        val initialMillis = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        endDate = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Annuler") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun eventDate(event: VolleyEvent) = java.time.Instant.ofEpochMilli(event.startsAt).atZone(ZoneId.systemDefault()).toLocalDate()
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
