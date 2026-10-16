@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gymapp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gymapp.R
import com.gymapp.data.Store
import com.gymapp.data.BackupCodec
import com.gymapp.domain.*
import com.gymapp.platform.PlatformController
import com.gymapp.platform.Reminders
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Spanish = Locale.forLanguageTag("es-ES")
private fun Double.fmt(digits: Int = 0) = String.format(Spanish, "%.${digits}f", this)
private fun String.number() = replace(',', '.').toDoubleOrNull()
private fun Long.clock() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun mealName(slot: String) = when(slot) { "breakfast" -> "Desayuno"; "lunch" -> "Comida"; "dinner" -> "Cena"; else -> "Snack" }
private fun sessionName(status: SessionStatus) = when(status) { SessionStatus.COMPLETED -> "Completa"; SessionStatus.ABANDONED -> "Incompleta"; SessionStatus.EMERGENCY_EXIT -> "Emergencia"; SessionStatus.SENSOR_UNVERIFIED -> "Pasos sin verificar"; SessionStatus.CARDIO_PENDING -> "Cardio pendiente"; else -> "Pesas en curso" }
private fun nutrition(recipe: RecipeVersion, foods: List<FoodVersion>, grams: Map<String,Double> = emptyMap(), portions: Double = 1.0): Nutrition = recipe.ingredients.fold(Nutrition()) { total, item ->
    val food = requireNotNull(foods.filter { it.id == item.foodId && (item.foodVersion == null || it.version == item.foodVersion) }.maxByOrNull { it.version }) { "No se encuentra la versión del ingrediente ${item.foodId}" }
    total + food.nutrition * ((grams[item.foodId] ?: item.grams) * portions / recipe.servings / food.referenceGrams)
}

@Composable
fun GymApp(store: Store) {
    GymTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val state by store.state.collectAsState()
        val platform = remember { PlatformController(context) }
        var destination by rememberSaveable { mutableIntStateOf(0) }
        var message by remember { mutableStateOf<String?>(null) }
        var showWelcome by rememberSaveable { mutableStateOf(false) }
        var permissionTick by remember { mutableIntStateOf(0) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        var importMode by remember { mutableStateOf("backup") }
        val snack = remember { SnackbarHostState() }
        var shownBackupFailure by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state) {
            val failure = store.backupError
            if(failure != null && failure != shownBackupFailure) message = failure
            shownBackupFailure = failure
        }
        val scrollState = rememberScrollState()
        LaunchedEffect(destination) { scrollState.scrollTo(0) }
        fun action(block: () -> Unit) { runCatching(block).onFailure { message = it.message ?: "No se pudo completar la acción" } }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) action {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportJson()) } ?: error("No se pudo abrir el archivo")
                message = "Copia exportada correctamente"
            }
        }
        var pendingImport by remember { mutableStateOf<String?>(null) }
        val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) action {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while(output.size() <= BackupCodec.MAX_BYTES) { val read = input.read(buffer, 0, minOf(buffer.size, BackupCodec.MAX_BYTES + 1 - output.size())); if(read < 0) break; output.write(buffer, 0, read) }
                    output.toByteArray()
                } ?: error("No se pudo leer el archivo")
                require(bytes.size <= BackupCodec.MAX_BYTES) { "El archivo supera el máximo de 8 MB" }
                val text = bytes.toString(Charsets.UTF_8)
                if (importMode == "profile") { store.importProfileJson(text); message = "Perfil importado"; showWelcome = false }
                else pendingImport = text
            }
        }
        val health = rememberLauncherForActivityResult(platform.healthPermissionsContract()) { permissionTick++ }
        val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++ }
        LaunchedEffect(message) { message?.let { snack.showSnackbar(it); message = null } }
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("welcome", 0)
            showWelcome = !prefs.getBoolean("seen", false)
            prefs.edit().putBoolean("seen", true).apply()
        }
        val session = state.activeSession
        LaunchedEffect(session?.id, session?.steps?.source, session?.focusRequested, session?.weightsCompletedAt) {
            platform.syncSession(session?.id, session?.startedAt ?: 0, session?.steps?.source ?: "none", session?.focusRequested ?: false, session?.weightsCompletedAt != null)
            if(session?.steps?.source == "sensor") platform.startPhoneTracking()
        }
        LaunchedEffect(state.settings) { Reminders.schedule(context) }
        LaunchedEffect(session?.id, resumed) {
            while (resumed) {
                permissionTick++
                val active = store.state.value.activeSession
                if (active?.steps?.source == "health_connect") platform.refreshHealth()
                else if(active != null) platform.stepReading()
                delay(30000)
            }
        }
        val permissions = remember(permissionTick) { platform.permissionStatus() }
        val tabs = listOf(R.string.today, R.string.train, R.string.meals, R.string.progress, R.string.settings)
        val symbols = listOf(Icons.Outlined.Home, Icons.Outlined.FitnessCenter, Icons.Outlined.Restaurant, Icons.Outlined.ShowChart, Icons.Outlined.Settings)
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, title -> NavigationBarItem(selected = destination == index, onClick = { destination = index }, modifier = Modifier.testTag("nav_$index"), icon = { Icon(symbols[index], contentDescription = null) }, label = { Text(stringResource(title), fontSize = 11.sp) }) }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(8.dp))
                when(destination) {
                    0 -> TodayScreen(store, state, platform, { destination = 1 }, { destination = 2 }) { action(it) }
                    1 -> TrainingScreen(store, state, platform) { action(it) }
                    2 -> MealsScreen(store, state) { action(it) }
                    3 -> ProgressScreen(store, state) { action(it) }
                    4 -> SettingsScreen(store, state, platform, permissionTick,
                        onExport = { export.launch("GymApp-${LocalDate.now()}.json") },
                        onImport = { importMode = "backup"; import.launch(arrayOf("application/json", "text/plain")) },
                        onProfile = { importMode = "profile"; import.launch(arrayOf("application/json", "text/plain")) },
                        onHealth = { health.launch(platform.healthPermissions) },
                        onRuntime = { val requested = buildList { if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }; runtime.launch(requested.toTypedArray()) }
                    ) { action(it) }
                }
            }
        }
        if (showWelcome) AlertDialog(onDismissRequest = { showWelcome = false }, title = { Text("Tu constancia, a tu ritmo") }, text = { Text("Entrena, organiza tus comidas y observa tu progreso. Tus datos se guardan en este teléfono. Puedes comenzar sin permisos e importar tu perfil cuando quieras.") }, confirmButton = { TextButton(onClick = { showWelcome = false }) { Text("Empezar") } }, dismissButton = { TextButton(onClick = { importMode = "profile"; import.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar perfil") } })
        if (pendingImport != null) AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("¿Restaurar esta copia?") }, text = { Text("Se validará el archivo antes de reemplazar los datos locales. Exporta una copia si quieres conservar tu estado actual.") }, confirmButton = { TextButton(onClick = { action { require(store.state.value.activeSession == null) { "Termina o abandona la sesión antes de restaurar una copia" }; store.importJson(pendingImport!!); pendingImport = null; message = "Copia restaurada" } }) { Text("Validar y restaurar") } }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun Heading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(Spanish), color = Lime, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PanelCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun MainButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text(text) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Lime, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun MacroLine(n: Nutrition) {
    Text("${n.kcal.fmt()} kcal  ·  ${n.protein.fmt(1)} g proteína", color = Lime, style = MaterialTheme.typography.titleSmall)
    Text("Grasas ${n.fat.fmt(1)} g · HC disponibles ${n.carbs.fmt(1)} g · Fibra ${n.fiber.fmt(1)} g", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TodayScreen(store: Store, state: AppState, platform: PlatformController, onTrain: () -> Unit, onMeals: () -> Unit, run: (() -> Unit) -> Unit) {
    val today = LocalDate.now()
    val session = state.activeSession
    val next = store.routines[state.nextRoutineIndex % store.routines.size]
    val meals = state.mealLogs.filter { it.date == today.toString() }
    val consumed = meals.fold(Nutrition()) { a, b -> a + b.nutrition }
    Heading(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Spanish)), "Hoy cuenta.", "Una sesión. Una comida. Un paso cada vez.")
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if(session == null) "TU SIGUIENTE SESIÓN" else "SESIÓN EN CURSO", color = Lime, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
            Text(if(session == null) next.name else store.routines.find { it.id == session.routineId }?.name ?: "Entrenamiento", style = MaterialTheme.typography.headlineMedium)
            Text(if(session == null) "${next.exercises.size} ejercicios · sin límite de tiempo" else "${session.workSets.size} series guardadas · ${sessionName(session.status)}", color = Muted)
            MainButton(if(session == null) stringResource(R.string.start_training) else stringResource(R.string.continue_training), { run { if(session == null) store.startSession(); onTrain() } })
        }
    }
    PanelCard("Tu alimentación de hoy") {
        if(meals.isEmpty()) Text("Aún no hay consumo registrado", color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("de ${state.settings.energyTarget} kcal", if(meals.isEmpty()) "Sin datos" else consumed.kcal.fmt(), Modifier.weight(1f))
            Stat("de ${state.settings.proteinTarget} g proteína", if(meals.isEmpty()) "Sin datos" else "${consumed.protein.fmt()} g", Modifier.weight(1f))
        }
        LinearProgressIndicator(progress = { (consumed.kcal / state.settings.energyTarget).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
        store.planFor(today).forEach { (slot, recipe) ->
            val recorded = meals.any { it.plannedKey == "$today:$slot" }
            Row(Modifier.fillMaxWidth().toggleable(recorded, role = Role.Checkbox) { checked -> run { store.setMealConsumed(today, slot, checked) } }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = recorded, onCheckedChange = null)
                Column(Modifier.weight(1f)) { Text(mealName(slot), color = Muted, style = MaterialTheme.typography.labelSmall); Text(recipe.name, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if(store.planFor(today).isEmpty()) Text("Fin de semana libre. Registra lo que elijas comer.", color = Muted)
        TextButton(onClick = onMeals) { Text("Ver comidas y cantidades →") }
    }
    if(session != null) StepPanel(session)
    PanelCard("Conexiones y permisos") {
        val p = platform.permissionStatus()
        Text("Concentración: ${if(p.accessibilityEnabled && p.focusServiceConnected) "servicio disponible" else "protección inactiva"}")
        Text("Pasos: ${when(state.settings.stepSource) { "health_connect" -> "Health Connect"; "sensor" -> "sensor del móvil"; else -> "sin fuente seleccionada" }}", color = Muted)
        Text("Mi Fitness solo figura como origen cuando aporta registros. No se suman reloj y teléfono.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    Text("Todo local. Sin cuentas, anuncios ni conexión obligatoria.", style = MaterialTheme.typography.bodySmall, color = Muted)
}

@Composable
private fun StepPanel(session: WorkoutSession) {
    val context = LocalContext.current
    val origins = remember(session.id, session.steps.lastReadAt) { PlatformController(context).stepReading().origins }
    PanelCard("Pasos desde el inicio") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("de ${session.stepGoal} pasos", if(session.steps.lastReadAt == null) "Sin datos" else session.steps.count.toString())
            Stat("restantes", session.stepsRemaining.toString())
        }
        LinearProgressIndicator(progress = { (session.steps.count.toFloat() / session.stepGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text("${when(session.steps.source) { "health_connect" -> "Health Connect"; "sensor" -> "Sensor del móvil"; else -> "Sin fuente" }} · ${session.steps.status}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Inicio ${session.startedAt.clock()} · Última lectura ${session.steps.lastReadAt?.clock() ?: "Sin datos"}", color = Muted, style = MaterialTheme.typography.bodySmall)
        if(session.steps.source == "health_connect" && origins.isNotEmpty()) Text("Orígenes de esta lectura: ${origins.joinToString { if(it == "com.xiaomi.wearable") "Mi Fitness" else it }}", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrainingScreen(store: Store, state: AppState, platform: PlatformController, run: (() -> Unit) -> Unit) {
    val session = state.activeSession
    val routine = store.routines.find { it.id == session?.routineId } ?: store.routines[state.nextRoutineIndex % store.routines.size]
    var editSet by remember { mutableStateOf<Pair<PlannedExercise, SetLog?>?>(null) }
    var editPlan by remember { mutableStateOf<PlannedExercise?>(null) }
    var omission by remember { mutableStateOf(false) }
    var abandon by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.id) {
        editSet = null; editPlan = null; omission = false; abandon = false; reason = ""
        while(true) { now = System.currentTimeMillis(); delay(1000) }
    }
    Heading(if(session == null) "Tu siguiente sesión" else "Guardado al instante", routine.name, if(session?.abbreviated == true) "Modo abreviado · cuenta lo que sí haces" else "Técnica primero. La carga llegará.")
    if(session == null) {
        PanelCard {
            Text("La secuencia continúa donde la dejaste, aunque cambie la fecha.", color = Muted)
            MainButton("Empezar entrenamiento", { run { store.startSession() } })
            OutlinedButton(onClick = { run { store.startSession(true) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Hoy me cuesta · plan abreviado") }
            Text("Abreviado: primeros tres ejercicios, dos series cada uno. Puedes editarlo y se guardará como abreviado.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    } else {
        PanelCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("series efectivas", session.workSets.size.toString())
                Stat("inicio · sin límite", session.startedAt.clock())
            }
            val p = platform.permissionStatus()
            Text(if(session.focusRequested && p.accessibilityEnabled && p.focusServiceConnected) "Concentración activa · GymApp y Spotify" else "Concentración: protección inactiva", color = if(p.focusServiceConnected && session.focusRequested) Lime else Muted)
            if(p.spotifyInstalled) TextButton(onClick = { platform.openSpotify() }) { Text("Abrir Spotify ↗") } else Text("Spotify no está instalado", color = Muted, style = MaterialTheme.typography.bodySmall)
            if(session.restEndsAt != null && session.restEndsAt > now) {
                val left = (session.restEndsAt - now + 999) / 1000
                Text("Descanso  ${left / 60}:${(left % 60).toString().padStart(2, '0')}", color = Lime, style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { run { platform.cancelRest(); store.update { it.copy(activeSession = it.activeSession?.copy(restEndsAt = null)) } } }) { Text("Terminar descanso") }
            }
        }
    }
    if(session == null || session.status == SessionStatus.WEIGHTS_ACTIVE) {
        (session?.plan ?: routine.exercises).forEachIndexed { index, planned ->
            val exercise = store.catalog.exercises.find { it.id == planned.exerciseId }
            val logs = session?.sets?.filter { it.exerciseId == planned.exerciseId }.orEmpty()
            val previous = state.workouts.asReversed().firstOrNull { w -> w.sets.any { it.exerciseId == planned.exerciseId && !it.warmup } }?.sets?.filter { it.exerciseId == planned.exerciseId && !it.warmup }.orEmpty()
            PanelCard {
                Text("${(index+1).toString().padStart(2,'0')}  ${exercise?.name ?: planned.exerciseId}", style = MaterialTheme.typography.titleMedium)
                Text("${planned.sets} series · ${planned.repsMin}–${planned.repsMax} rep · ${planned.restSeconds} s descanso · RIR ${planned.targetRir}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(if(exercise?.loadUnit == "kg_per_dumbbell") "Carga en kg por mancuerna" else "Carga en kg total", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(if(previous.isEmpty()) "Última vez: Sin datos" else "Última vez: ${previous.joinToString(" · ") { "${it.weight.fmt(1)} kg × ${it.reps}" }}", color = Muted, style = MaterialTheme.typography.bodySmall)
                logs.forEach { set ->
                    OutlinedButton(onClick = { editSet = planned to set }, modifier = Modifier.fillMaxWidth()) {
                        Text("${if(set.warmup) "Calentamiento" else "Serie ${set.index}"} · ${set.weight.fmt(1)} kg × ${set.reps}${set.rir?.let { " · RIR $it" } ?: ""}  ✎")
                    }
                    if(set.note.isNotBlank()) Text(set.note, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if(session != null) {
                    MainButton("+ Registrar serie", { editSet = planned to null })
                    val suggestion = Rules.progression(planned, if(logs.none { !it.warmup }) previous else logs, state.settings.equipmentIncrement)
                    if(suggestion != null) {
                        Text("Todas las series alcanzan el rango alto con técnica y esfuerzo adecuados. Sugerencia: ${suggestion.fmt(1)} kg. Puedes repetir la carga.", color = Lime, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { editSet = planned to SetLog(exerciseId = planned.exerciseId, index = logs.count { !it.warmup } + 1, weight = suggestion, reps = planned.repsMin) }) { Text("Aceptar peso para la próxima serie") }
                    }
                    TextButton(onClick = { run { store.startRest(planned.restSeconds); platform.startRest(planned.restSeconds) } }) { Text("Iniciar descanso") }
                }
                TextButton(onClick = { editPlan = planned }) { Text("Editar plan / alternativa") }
            }
        }
        if(session != null) MainButton("Terminar pesas", { omission = true; reason = "" })
    }
    if(session != null) {
        StepPanel(session)
        if(session.steps.source == "sensor") OutlinedButton(onClick = { run { platform.startPhoneTracking() } }, modifier = Modifier.fillMaxWidth()) { Text("Activar / reanudar sensor del móvil") }
        if(session.status == SessionStatus.CARDIO_PENDING) {
            PanelCard("Pesas guardadas") {
                Text("Los avisos de ausencia ya están detenidos. Completa los pasos de esta sesión para cerrarla normalmente.", color = Muted)
                if(state.settings.timedCardioEnabled && session.timedCardioMinutes == null) OutlinedButton(onClick = { run { store.chooseTimedCardio() } }, modifier = Modifier.fillMaxWidth()) { Text("Elegir cardio por tiempo · ${state.settings.timedCardioMinutes} min") }
                if(session.cardioStartedAt != null && session.timedCardioMinutes != null) {
                    val remaining = (session.timedCardioMinutes * 60L - (now - session.cardioStartedAt)/1000).coerceAtLeast(0)
                    Text("Cardio elegido · ${remaining / 60}:${(remaining % 60).toString().padStart(2,'0')} restantes", color = Lime)
                    Text("Objetivo alternativo elegido expresamente. No se convierte en pasos.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                MainButton("Cerrar sesión completa", { run { store.closeSession(SessionStatus.COMPLETED) } })
                TextButton(onClick = { run { store.closeSession(SessionStatus.SENSOR_UNVERIFIED, "Fallo real de sensor o sincronización") } }) { Text("Cerrar por fallo de sensor") }
            }
        }
        OutlinedButton(onClick = { abandon = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Abandonar sesión") }
        TextButton(onClick = { run { platform.emergencyExit(); if(store.state.value.activeSession != null) store.closeSession(SessionStatus.EMERGENCY_EXIT, "Salida de emergencia") } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Salida de emergencia", color = MaterialTheme.colorScheme.error) }
        Text("Si hay dolor o urgencia, sal directamente. Android permite revocar permisos y acceder a funciones esenciales.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    if(editSet != null && session != null) {
        val (plan, existing) = editSet!!
        val previous = state.workouts.asReversed().flatMap { it.sets.asReversed() }.firstOrNull { it.exerciseId == plan.exerciseId && !it.warmup }
        SetEditor(plan, existing, previous, session, onDismiss = { editSet = null }, onDelete = if(existing != null && session.sets.any { it.id == existing.id }) ({ run { store.update { it.copy(activeSession = it.activeSession?.copy(sets = it.activeSession.sets.filterNot { s -> s.id == existing.id })) }; editSet = null } }) else null) { set -> run { store.saveSet(set); editSet = null; store.startRest(plan.restSeconds); platform.startRest(plan.restSeconds) } }
    }
    if(editPlan != null) PlanEditor(editPlan!!, store.catalog.exercises, onDismiss = { editPlan = null }) { updated -> run {
        if(session != null) {
            require(updated.exerciseId == editPlan!!.exerciseId || session.sets.none { it.exerciseId == editPlan!!.exerciseId }) { "Este ejercicio ya tiene series. Consérvalo o elimina sus series antes de sustituirlo." }
            store.update { it.copy(activeSession = it.activeSession?.copy(plan = it.activeSession.plan.map { p -> if(p == editPlan) updated else p })) }
        }
        else store.update { s -> val revised = routine.copy(exercises = routine.exercises.map { if(it == editPlan) updated else it }); s.copy(routineOverrides = s.routineOverrides.filterNot { it.id == routine.id } + revised) }
        editPlan = null
    } }
    if(omission && session != null) {
        val missing = session.plan.filter { p -> session.workSets.count { it.exerciseId == p.exerciseId } < p.sets }
        AlertDialog(onDismissRequest = { omission = false }, title = { Text(if(missing.isEmpty()) "¿Terminar las pesas?" else "Confirmar ejercicios o series omitidos") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if(missing.isEmpty()) "Se conservarán todas tus series. Después quedan los pasos o el cardio elegido." else "Quedan ${missing.size} ejercicios con series pendientes. Se guardará el trabajo real y el motivo de lo omitido.")
                if(missing.isNotEmpty()) OutlinedTextField(reason, { reason = it }, label = { Text("Motivo obligatorio") })
                if(session.workSets.isEmpty()) Text("Necesitas al menos una serie efectiva. Puedes usar abandono o emergencia.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = session.workSets.isNotEmpty() && (missing.isEmpty() || reason.isNotBlank()), onClick = { run { store.finishWeights(reason); omission = false } }) { Text("Guardar pesas") } }, dismissButton = { TextButton(onClick = { omission = false }) { Text("Seguir entrenando") } })
    }
    if(abandon && session != null) AlertDialog(onDismissRequest = { store.cancelAbandon(); abandon = false }, title = { Text("Abandono · ${session.abandonmentConfirmations + 1}/10") }, text = { Column {
        Text("La sesión se guardará como incompleta. Puedes volver ahora; la cadena se reinicia al cancelar.")
        TextButton(onClick = { run { platform.emergencyExit(); if(store.state.value.activeSession != null) store.closeSession(SessionStatus.EMERGENCY_EXIT, "Salida de emergencia"); abandon = false } }) { Text("Salida de emergencia", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { run { if(store.confirmAbandon()) abandon = false } }) { Text(if(session.abandonmentConfirmations >= 9) "Confirmar abandono" else "Confirmar ${session.abandonmentConfirmations + 1}/10") } }, dismissButton = { TextButton(onClick = { store.cancelAbandon(); abandon = false }) { Text("Volver al entrenamiento") } })
}

@Composable
private fun SetEditor(plan: PlannedExercise, existing: SetLog?, previous: SetLog?, session: WorkoutSession, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (SetLog) -> Unit) {
    var weight by remember { mutableStateOf((existing?.weight ?: previous?.weight)?.toString() ?: "") }
    var reps by remember { mutableStateOf(existing?.reps?.toString() ?: "") }
    var rir by remember { mutableStateOf(existing?.rir?.toString() ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var warmup by remember { mutableStateOf(existing?.warmup ?: false) }
    var technique by remember { mutableStateOf(existing?.techniqueGood ?: true) }
    val valid = weight.number()?.let { it.isFinite() && it in 0.0..2000.0 } == true && reps.toIntOrNull()?.let { it in 1..200 } == true && (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..10 } == true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if(existing != null) "Editar serie" else "Registrar serie") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Objetivo ${plan.repsMin}–${plan.repsMax} rep · esfuerzo opcional", color = Muted)
            NumberField(weight, { weight = it }, "Carga (kg)")
            NumberField(reps, { reps = it }, "Repeticiones")
            NumberField(rir, { rir = it }, "RIR · repeticiones en reserva (opcional)")
            CheckRow("Serie de calentamiento", warmup) { warmup = it }
            CheckRow("Técnica adecuada", technique) { technique = it }
            OutlinedTextField(note, { if(it.length <= 1000) note = it }, label = { Text("Nota opcional") })
            if(onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar serie", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(SetLog(id = existing?.id ?: newId(), exerciseId = plan.exerciseId, index = if(existing != null && existing.warmup == warmup) existing.index else ((session.sets.filter { it.exerciseId == plan.exerciseId && it.warmup == warmup }.maxOfOrNull { it.index } ?: 0) + 1), weight = weight.number()!!, reps = reps.toInt(), warmup = warmup, rir = rir.toIntOrNull(), note = note, techniqueGood = technique)) }) { Text("Guardar serie") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PlanEditor(plan: PlannedExercise, exercises: List<Exercise>, onDismiss: () -> Unit, onSave: (PlannedExercise) -> Unit) {
    var selected by remember { mutableStateOf(plan.exerciseId) }
    var sets by remember { mutableStateOf(plan.sets.toString()) }
    var low by remember { mutableStateOf(plan.repsMin.toString()) }
    var high by remember { mutableStateOf(plan.repsMax.toString()) }
    var rest by remember { mutableStateOf(plan.restSeconds.toString()) }
    val exercise = exercises.find { it.id == plan.exerciseId }
    val options = (listOfNotNull(exercise) + exercises.filter { it.id in exercise?.alternatives.orEmpty() }).distinctBy { it.id }
    val valid = sets.toIntOrNull() in 1..20 && low.toIntOrNull() in 1..100 && high.toIntOrNull() in 1..100 && (high.toIntOrNull() ?: 0) >= (low.toIntOrNull() ?: 1) && rest.toIntOrNull() in 5..1800
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar ejercicio") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { e -> RadioRow(e.name, selected == e.id) { selected = e.id } }
            Text("El historial se conserva por ejercicio realmente realizado.", color = Muted, style = MaterialTheme.typography.bodySmall)
            NumberField(sets, { sets = it }, "Series")
            NumberField(low, { low = it }, "Repeticiones mínimas")
            NumberField(high, { high = it }, "Repeticiones máximas")
            NumberField(rest, { rest = it }, "Descanso (segundos)")
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(plan.copy(exerciseId = selected, sets = sets.toInt(), repsMin = low.toInt(), repsMax = high.toInt(), restSeconds = rest.toInt())) }) { Text("Guardar plan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, null); Text(label, Modifier.weight(1f)) }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, null); Text(label, Modifier.weight(1f)) }
}

@Composable
private fun MealsScreen(store: Store, state: AppState, run: (() -> Unit) -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateText)
    var editor by remember { mutableStateOf<Pair<String, RecipeVersion>?>(null) }
    var foodLog by remember { mutableStateOf(false) }
    var foodEditor by remember { mutableStateOf(false) }
    var shopping by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf<RecipeVersion?>(null) }
    var loggedEditor by remember { mutableStateOf<MealLog?>(null) }
    var repeatEditor by remember { mutableStateOf<MealLog?>(null) }
    var repeatDate by remember { mutableStateOf(date.plusDays(1).toString()) }
    val plan = store.planFor(date)
    val logs = state.mealLogs.filter { it.date == dateText }
    val total = logs.fold(Nutrition()) { a,b -> a+b.nutrition }
    val plannedTotal = plan.fold(Nutrition()) { a, pair -> a + store.mealSnapshot(date, pair.first).nutrition }
    Heading("Nutrición sin complicaciones", "Comidas", "Cantidades en crudo o seco; lácteos, pan y tortillas como se venden.")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = { dateText = date.minusDays(1).toString() }, modifier = Modifier.semantics { contentDescription = "Día anterior" }) { Text("←") }
        Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMM", Spanish)), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { dateText = date.plusDays(1).toString() }, modifier = Modifier.semantics { contentDescription = "Día siguiente" }) { Text("→") }
    }
    PanelCard("Consumido") {
        if(logs.isEmpty()) Text("Sin datos · no has registrado comidas", color = Muted) else MacroLine(total)
        Text("Objetivo ${state.settings.energyTarget} kcal · ${state.settings.proteinTarget} g proteína", color = Muted, style = MaterialTheme.typography.bodySmall)
        if(plan.isNotEmpty()) Text("Planificado: ${plannedTotal.kcal.fmt()} kcal · ${plannedTotal.protein.fmt(1)} g proteína. Solo lo marcado cuenta como consumo.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    plan.forEach { (slot, recipe) ->
        val snapshot = store.mealSnapshot(date, slot)
        val consumed = logs.firstOrNull { it.plannedKey == "$date:$slot" }
        PanelCard {
            Text(if(slot == "snack") state.settings.snackTiming else mealName(slot), color = Lime, style = MaterialTheme.typography.labelMedium)
            Text(recipe.name, style = MaterialTheme.typography.titleLarge)
            MacroLine(snapshot.nutrition)
            CheckRow(if(consumed != null) "Registrada · desmarcar para deshacer" else "Marcar como consumida", consumed != null) { checked -> run { store.setMealConsumed(date, slot, checked) } }
            if(consumed != null && consumed.nutrition != snapshot.nutrition) Text("El consumo conserva su versión anterior: ${consumed.nutrition.kcal.fmt()} kcal. Editar no cambia registros pasados sin confirmación.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { editor = slot to recipe }) { Text("Ver / editar / sustituir") }
                TextButton(onClick = { batch = recipe }) { Text("Por tandas") }
            }
        }
    }
    if(plan.isEmpty()) PanelCard("Día libre") { Text("Sin menú obligatorio. Puedes registrar alimentos o elegir una receta de la biblioteca. Un día sin registro no equivale a cero calorías.", color = Muted) }
    logs.filter { it.plannedKey == null }.forEach { log -> PanelCard(log.name) {
        MacroLine(log.nutrition)
        Text(log.ingredients.joinToString(" · ") { "${it.grams.fmt()} g ${it.food.name}" }, color = Muted)
        Row { TextButton(onClick = { loggedEditor = log }, modifier = Modifier.weight(1f)) { Text("Editar gramos") }; TextButton(onClick = { repeatEditor = log; repeatDate = date.plusDays(1).toString() }, modifier = Modifier.weight(1f)) { Text("Repetir otra fecha") } }
        TextButton(onClick = { run { store.deleteMeal(log.id) } }) { Text("Deshacer consumo") }
    } }
    MainButton("+ Registrar alimento", { foodLog = true })
    OutlinedButton(onClick = { library = !library }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Recetas y favoritos") }
    if(library) store.allRecipes.forEach { recipe -> PanelCard(recipe.name) {
        Text("${nutrition(recipe, store.catalog.foods + state.customFoods).kcal.fmt()} kcal por ración", color = Muted)
        Row { TextButton(onClick = { editor = recipe.slot to recipe }) { Text("Elegir") }; TextButton(onClick = { run { store.update { it.copy(favoriteRecipes = if(recipe.id in it.favoriteRecipes) it.favoriteRecipes - recipe.id else it.favoriteRecipes + recipe.id) } } }) { Text(if(recipe.id in state.favoriteRecipes) "★ Favorita" else "☆ Guardar") } }
    } }
    OutlinedButton(onClick = { foodEditor = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Crear / editar alimento de etiqueta") }
    OutlinedButton(onClick = { shopping = !shopping }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Lista de la compra") }
    if(shopping) ShoppingPanel(store, state, date, run)
    Text("La energía del origen es autoritativa. Los hidratos mostrados son disponibles, con la fibra separada. USDA SR Legacy / etiquetas: fuentes incluidas en el catálogo.", color = Muted, style = MaterialTheme.typography.bodySmall)
    if(editor != null) MealEditor(store, state, date, editor!!.first, editor!!.second, { editor = null }, run)
    if(foodLog) FoodLogEditor(store, date, { foodLog = false }, run)
    if(foodEditor) FoodEditor(store, { foodEditor = false }, run)
    if(batch != null) BatchEditor(batch!!, store.catalog.foods + state.customFoods, { batch = null })
    if(loggedEditor != null) LoggedMealEditor(loggedEditor!!, { loggedEditor = null }) { revised -> run { store.update { it.copy(mealLogs = it.mealLogs.map { meal -> if(meal.id == revised.id) revised else meal }) }; loggedEditor = null } }
    if(repeatEditor != null) AlertDialog(onDismissRequest = { repeatEditor = null }, title = { Text("Repetir consumo en otra fecha") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Se copiarán las cantidades y los datos de etiqueta originales de ${repeatEditor!!.name}.", color = Muted)
        OutlinedTextField(repeatDate, { repeatDate = it }, label = { Text("Fecha · AAAA-MM-DD") }, singleLine = true)
    } }, confirmButton = { TextButton(enabled = runCatching { LocalDate.parse(repeatDate) }.isSuccess, onClick = { run { store.repeatMeal(repeatEditor!!, LocalDate.parse(repeatDate)); repeatEditor = null } }) { Text("Repetir consumo") } }, dismissButton = { TextButton(onClick = { repeatEditor = null }) { Text("Cancelar") } })
}

@Composable
private fun LoggedMealEditor(meal: MealLog, onDismiss: () -> Unit, onSave: (MealLog) -> Unit) {
    var grams by remember { mutableStateOf(meal.ingredients.map { it.grams.toString() }) }
    val valid = grams.all { value -> value.number()?.let { it.isFinite() && it in 0.1..10000.0 } == true }
    val ingredients = meal.ingredients.mapIndexed { index, snapshot -> snapshot.copy(grams = grams[index].number()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar consumo registrado") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(meal.name, style = MaterialTheme.typography.titleMedium)
            MacroLine(Rules.nutrition(ingredients))
            meal.ingredients.forEachIndexed { index, snapshot -> NumberField(grams[index], { value -> grams = grams.toMutableList().also { it[index] = value } }, "${snapshot.food.name} (g)") }
            Text("Se conservan las versiones de etiqueta originales. Solo cambian las cantidades de este registro.", color = Muted)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(meal.copy(ingredients = ingredients, nutrition = Rules.nutrition(ingredients))) }) { Text("Guardar consumo") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun MealEditor(store: Store, state: AppState, date: LocalDate, slot: String, initial: RecipeVersion, onDismiss: () -> Unit, run: (() -> Unit) -> Unit) {
    var selected by remember { mutableStateOf(initial.id) }
    var portions by remember { mutableStateOf(state.mealOverrides.find { it.date == date.toString() && it.slot == slot }?.portions?.toString() ?: "1") }
    var replacements by remember { mutableStateOf(false) }
    var substitution by remember { mutableStateOf<String?>(null) }
    val recipe = if(selected == initial.id) initial else store.allRecipes.find { it.id == selected } ?: initial
    var ingredients by remember(selected) { mutableStateOf(recipe.ingredients) }
    var grams by remember(selected) { mutableStateOf(recipe.ingredients.associate { it.foodId to (state.mealOverrides.find { o -> o.date == date.toString() && o.slot == slot && o.recipeId == selected }?.grams?.get(it.foodId) ?: it.grams).toString() }) }
    var applyConsumed by remember { mutableStateOf(false) }
    val consumed = state.mealLogs.any { it.plannedKey == "$date:$slot" }
    val valid = portions.number()?.let { it.isFinite() && it in 0.01..100.0 } == true && ingredients.all { i -> grams[i.foodId]?.number()?.let { it.isFinite() && it in 0.1..10000.0 } == true }
    val edited = recipe.copy(ingredients = ingredients)
    val computed = nutrition(edited, store.catalog.foods + state.customFoods, grams.mapNotNull { (id,g) -> g.number()?.let { id to it } }.toMap(), portions.number() ?: 1.0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${mealName(slot)} · cantidades") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleMedium)
            MacroLine(computed)
            TextButton(onClick = { replacements = !replacements }) { Text("Sustituir plato") }
            if(replacements) store.allRecipes.filter { it.slot == slot || it.id in state.favoriteRecipes }.forEach { candidate -> TextButton(onClick = { selected = candidate.id; replacements = false }) { Text(candidate.name) } }
            NumberField(portions, { portions = it }, "Raciones")
            ingredients.forEach { ingredient ->
                val food = (store.catalog.foods + state.customFoods).filter { it.id == ingredient.foodId && (ingredient.foodVersion == null || ingredient.foodVersion == it.version) }.maxBy { it.version }
                NumberField(grams[ingredient.foodId].orEmpty(), { v -> grams = grams + (ingredient.foodId to v) }, "${food.name} (g)")
                TextButton(onClick = { substitution = if(substitution == ingredient.foodId) null else ingredient.foodId }) { Text("Sustituir ingrediente") }
                if(substitution == ingredient.foodId) store.allFoods.forEach { alternative -> TextButton(enabled = ingredients.none { it.foodId == alternative.id }, onClick = {
                    val previousFood = (store.catalog.foods + state.customFoods).filter { it.id == ingredient.foodId && (ingredient.foodVersion == null || ingredient.foodVersion == it.version) }.maxBy { it.version }
                    val oldGrams = grams[ingredient.foodId]?.number()?.takeIf { it.isFinite() && it > 0 } ?: ingredient.grams
                    val oriented = if(previousFood.kcal > 0 && alternative.kcal > 0) (oldGrams * previousFood.kcal / previousFood.referenceGrams * alternative.referenceGrams / alternative.kcal).coerceIn(0.1, 10000.0) else oldGrams
                    ingredients = ingredients.map { if(it.foodId == ingredient.foodId) it.copy(foodId = alternative.id, foodVersion = alternative.version) else it }
                    grams = grams - ingredient.foodId + (alternative.id to oriented.fmt(1))
                    substitution = null
                }) { Text(alternative.name + if(ingredients.any { it.foodId == alternative.id }) " · ya incluido" else "") } }
            }
            recipe.instructions.forEach { Text(it) }
            Text(recipe.storageNote, color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("Pesar en crudo/seco y solo la parte comestible. Al sustituir, la cantidad propuesta es orientativa según energía; no garantiza equivalencia de proteína ni de otros macros. Revisa las cantidades y el nuevo total.", color = Muted, style = MaterialTheme.typography.bodySmall)
            if(consumed) CheckRow("Actualizar también el consumo de este día", applyConsumed) { applyConsumed = it }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { run {
        store.saveMealEdit(edited, MealOverride(date = date.toString(), slot = slot, recipeId = recipe.id, portions = portions.number()!!, grams = grams.mapValues { it.value.number()!! }), consumed && applyConsumed)
        onDismiss()
    } }) { Text("Guardar cantidades") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun FoodLogEditor(store: Store, date: LocalDate, onDismiss: () -> Unit, run: (() -> Unit) -> Unit) {
    var selected by remember { mutableStateOf(store.allFoods.first().id) }
    var grams by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf("snack") }
    val food = store.allFoods.first { it.id == selected }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Registrar alimento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("Buscar en base local") })
            store.allFoods.filter { it.name.contains(search, true) }.forEach { option -> RadioRow(option.name, option.id == selected) { selected = option.id } }
            Text("Seleccionado: ${food.name}", color = Lime)
            NumberField(grams, { grams = it }, "Cantidad (g)")
            Row { listOf("breakfast", "lunch", "dinner", "snack").forEach { item -> TextButton(onClick = { slot = item }, contentPadding = PaddingValues(4.dp)) { Text(if(slot == item) "✓ ${mealName(item)}" else mealName(item), fontSize = 11.sp) } } }
            MacroLine(food.nutrition * ((grams.number() ?: 0.0)/food.referenceGrams))
        }
    }, confirmButton = { TextButton(enabled = grams.number()?.let { it.isFinite() && it in 0.1..10000.0 } == true, onClick = { run { store.logFood(date, slot, selected, grams.number()!!); onDismiss() } }) { Text("Registrar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun FoodEditor(store: Store, onDismiss: () -> Unit, run: (() -> Unit) -> Unit) {
    var selected by remember { mutableStateOf<FoodVersion?>(null) }
    var choose by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && listOf(kcal, protein, fat, carbs, fiber).all { v -> v.number()?.let { it.isFinite() && it >= 0 } == true } && (kcal.number() ?: 0.0) <= 1000 && listOf(protein, fat, carbs, fiber).all { (it.number() ?: 0.0) <= 100 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Alimento de etiqueta") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Todos los valores por 100 g. Los hidratos de etiquetas UE ya excluyen la fibra.", color = Muted)
            TextButton(onClick = { choose = !choose }) { Text("Elegir alimento para nueva versión") }
            if(choose) store.allFoods.forEach { f -> TextButton(onClick = { selected = f; name = f.name; kcal = f.kcal.toString(); protein = f.protein.toString(); fat = f.fat.toString(); carbs = f.carbs.toString(); fiber = f.fiber.toString(); choose = false }) { Text(f.name) } }
            OutlinedTextField(name, { if(it.length <= 150) name = it }, label = { Text("Nombre") })
            NumberField(kcal, { kcal = it }, "Energía (kcal)")
            NumberField(protein, { protein = it }, "Proteína (g)")
            NumberField(fat, { fat = it }, "Grasas (g)")
            NumberField(carbs, { carbs = it }, "Hidratos disponibles (g)")
            NumberField(fiber, { fiber = it }, "Fibra (g)")
            Text("Guardar crea una versión nueva. Los consumos anteriores conservan su etiqueta y cantidades originales.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { run { store.saveFood(FoodVersion(id = selected?.id ?: "custom_${newId()}", name = name.trim(), kcal = kcal.number()!!, protein = protein.number()!!, fat = fat.number()!!, carbs = carbs.number()!!, fiber = fiber.number()!!)); onDismiss() } }) { Text("Guardar alimento") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ShoppingPanel(store: Store, state: AppState, date: LocalDate, run: (() -> Unit) -> Unit) {
    var days by remember { mutableStateOf("5") }
    var portions by remember { mutableStateOf("1") }
    var edit by remember { mutableStateOf<ShoppingItem?>(null) }
    var amount by remember { mutableStateOf("") }
    PanelCard("Compra agregada") {
        Text("Desde ${date.format(DateTimeFormatter.ofPattern("d MMM", Spanish))}. Solo se agregan los días con menú o plan propio.", color = Muted)
        NumberField(days, { days = it }, "Número de días (1–14)")
        NumberField(portions, { portions = it }, "Raciones por menú")
        MainButton("Generar lista", { run { store.buildShopping((0 until days.toInt()).map { date.plusDays(it.toLong()) }, portions.number()!!) } }, days.toIntOrNull() in 1..14 && portions.number()?.let { it in 0.01..100.0 } == true)
        state.shopping.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { checked -> run { store.update { it.copy(shopping = it.shopping.map { i -> if(i.foodId == item.foodId) i.copy(checked = checked) else i }) } } }, modifier = Modifier.semantics { contentDescription = "Comprado: ${item.name}" })
                Text(item.name, Modifier.weight(1f), color = if(item.checked) Muted else MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { edit = item; amount = item.grams.toString() }) { Text(if(item.grams >= 1000) "${(item.grams/1000).fmt(2)} kg" else "${item.grams.fmt()} g") }
            }
        }
    }
    if(edit != null) AlertDialog(onDismissRequest = { edit = null }, title = { Text(edit!!.name) }, text = { NumberField(amount, { amount = it }, "Cantidad (g)") }, confirmButton = { TextButton(enabled = amount.number()?.let { it.isFinite() && it > 0 } == true, onClick = { run { store.update { it.copy(shopping = it.shopping.map { item -> if(item.foodId == edit!!.foodId) item.copy(grams = amount.number()!!) else item }) }; edit = null } }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancelar") } })
}

@Composable
private fun BatchEditor(recipe: RecipeVersion, foods: List<FoodVersion>, onDismiss: () -> Unit) {
    var portions by remember { mutableStateOf("4") }
    val count = portions.number()?.takeIf { it.isFinite() && it in 0.01..100.0 } ?: 0.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cocina por tandas") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleMedium)
            NumberField(portions, { portions = it }, "Número de raciones")
            recipe.ingredients.forEach { i -> Text("${(i.grams * count / recipe.servings).fmt()} g  ${foods.filter { it.id == i.foodId && (i.foodVersion == null || it.version == i.foodVersion) }.maxBy { it.version }.name}") }
            Text("Reparte el resultado cocinado en ${count.fmt(1)} porciones iguales. Las cantidades indicadas se pesan antes de cocinar.", color = Muted)
            recipe.instructions.forEach { Text(it) }
            Text(recipe.storageNote, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } })
}

@Composable
private fun ProgressScreen(store: Store, state: AppState, run: (() -> Unit) -> Unit) {
    var weightEditor by remember { mutableStateOf(false) }
    var wellbeingEditor by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<String?>(null) }
    var weightDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var kg by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val recent = state.weights.filter { LocalDate.parse(it.date) in today.minusDays(6)..today }
    val previous = state.weights.filter { LocalDate.parse(it.date) in today.minusDays(13)..today.minusDays(7) }
    val average = recent.takeIf { it.isNotEmpty() }?.map { it.kg }?.average()
    val trend = if(average != null && previous.isNotEmpty()) average - previous.map { it.kg }.average() else null
    Heading("Cada registro cuenta", "Progreso", "Observa la tendencia, sin perseguir un día perfecto.")
    PanelCard("Tu peso") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("última medición", state.weights.maxByOrNull { it.date }?.let { "${it.kg.fmt(1)} kg" } ?: "Sin datos")
            Stat("media últimos 7 días", average?.let { "${it.fmt(1)} kg" } ?: "Sin datos")
        }
        Text("${recent.size} días medidos de los últimos 7. Las fechas sin medición se excluyen de la media.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Tendencia frente a los 7 días anteriores: ${trend?.let { "${if(it > 0) "+" else ""}${it.fmt(2)} kg" } ?: "Sin datos"}", color = Muted)
        MainButton("+ Registrar peso", { kg = ""; weightDate = today.toString(); weightEditor = true })
        state.weights.sortedByDescending { it.date }.take(14).forEach { log -> TextButton(onClick = { kg = log.kg.toString(); weightDate = log.date; weightEditor = true }) { Text("${log.date}  ·  ${log.kg.fmt(1)} kg  ✎") } }
    }
    PanelCard("Cómo te encuentras") {
        val log = state.wellbeing.firstOrNull { it.date == today.toString() }
        if(log == null) Text("Sin datos hoy. Es un registro opcional.", color = Muted)
        else {
            Text("Hambre ${log.hunger ?: "Sin datos"} · Energía ${log.energy ?: "Sin datos"} · Rendimiento ${log.performance ?: "Sin datos"}", color = Muted)
            if(log.note.isNotBlank()) Text(log.note)
        }
        TextButton(onClick = { wellbeingEditor = true }) { Text("Registrar / editar sensaciones") }
        Text("Escala 1–5. No se realizan diagnósticos ni ajustes automáticos de calorías.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    Text("Historial de entrenamiento", style = MaterialTheme.typography.titleLarge)
    if(state.workouts.isEmpty()) PanelCard { Text("Tu historial aparecerá después de cerrar una sesión. Solo cuentan las series que registres.", color = Muted) }
    state.workouts.asReversed().forEach { workout ->
        PanelCard {
            Text(store.routines.find { it.id == workout.routineId }?.name ?: "Entrenamiento", style = MaterialTheme.typography.titleMedium)
            val date = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.of(workout.zoneId)).toLocalDate()
            Text("$date · ${sessionName(workout.status)}${if(workout.abbreviated) " · Abreviada" else ""}", color = Lime)
            Text("${workout.workSets.size} series efectivas · ${workout.steps.count} pasos registrados", color = Muted)
            if(workout.exitReason.isNotBlank()) Text(workout.exitReason, style = MaterialTheme.typography.bodySmall, color = Muted)
            TextButton(onClick = { history = if(history == workout.id) null else workout.id }) { Text(if(history == workout.id) "Ocultar series" else "Ver cargas y repeticiones") }
            if(history == workout.id) workout.sets.forEach { set ->
                val name = store.catalog.exercises.find { it.id == set.exerciseId }?.name ?: set.exerciseId
                val unit = if(store.catalog.exercises.find { it.id == set.exerciseId }?.loadUnit == "kg_per_dumbbell") "kg por mancuerna" else "kg total"
                Text("$name · ${set.weight.fmt(1)} $unit × ${set.reps}${if(set.warmup) " · calentamiento" else ""}${set.rir?.let { " · RIR $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                if(set.note.isNotBlank()) Text(set.note, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
    if(weightEditor) {
        val validDate = runCatching { LocalDate.parse(weightDate) }.getOrNull()
        AlertDialog(onDismissRequest = { weightEditor = false }, title = { Text("Registrar peso") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(weightDate, { weightDate = it }, label = { Text("Fecha · AAAA-MM-DD") }, singleLine = true)
                NumberField(kg, { kg = it }, "Peso (kg)")
                Text("Una medición por fecha. Guardar sobre la misma fecha actualiza su valor.", color = Muted)
            }
        }, confirmButton = { TextButton(enabled = validDate != null && !validDate.isAfter(today) && kg.number()?.let { it.isFinite() && it in 20.0..500.0 } == true, onClick = { run { store.saveWeight(validDate!!, kg.number()!!); weightEditor = false } }) { Text("Guardar peso") } }, dismissButton = { TextButton(onClick = { weightEditor = false }) { Text("Cancelar") } })
    }
    if(wellbeingEditor) WellbeingEditor(state.wellbeing.firstOrNull { it.date == today.toString() }, { wellbeingEditor = false }) { log -> run { store.saveWellbeing(log); wellbeingEditor = false } }
}

@Composable
private fun WellbeingEditor(initial: WellbeingLog?, onDismiss: () -> Unit, onSave: (WellbeingLog) -> Unit) {
    var hunger by remember { mutableStateOf(initial?.hunger?.toString() ?: "") }
    var energy by remember { mutableStateOf(initial?.energy?.toString() ?: "") }
    var performance by remember { mutableStateOf(initial?.performance?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val valid = listOf(hunger, energy, performance).all { it.isBlank() || it.toIntOrNull() in 1..5 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sensaciones de hoy") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Opcional: 1 = muy baja, 5 = muy alta. Deja vacío lo que no quieras valorar.", color = Muted)
            NumberField(hunger, { hunger = it }, "Hambre (1–5)")
            NumberField(energy, { energy = it }, "Energía (1–5)")
            NumberField(performance, { performance = it }, "Rendimiento (1–5)")
            OutlinedTextField(note, { if(it.length <= 1000) note = it }, label = { Text("Nota opcional") })
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(WellbeingLog(date = LocalDate.now().toString(), hunger = hunger.toIntOrNull(), energy = energy.toIntOrNull(), performance = performance.toIntOrNull(), note = note)) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun SettingsScreen(store: Store, state: AppState, platform: PlatformController, tick: Int, onExport: () -> Unit, onImport: () -> Unit, onProfile: () -> Unit, onHealth: () -> Unit, onRuntime: () -> Unit, run: (() -> Unit) -> Unit) {
    var goals by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(false) }
    val p = remember(tick) { platform.permissionStatus() }
    Heading("A tu medida", "Ajustes", "Tus preferencias y tus datos, bajo tu control.")
    PanelCard("Perfil y objetivos") {
        Text("Objetivo diario: ${state.settings.energyTarget} kcal · ${state.settings.proteinTarget} g proteína", color = Muted)
        Text("Peso objetivo: ${state.profile.targetWeight?.let { "${it.fmt(1)} kg" } ?: "Sin datos"}", color = Muted)
        TextButton(onClick = { goals = true }) { Text("Editar objetivos, material y cardio") }
        TextButton(onClick = { profile = true }) { Text("Editar perfil") }
        TextButton(onClick = onProfile) { Text("Importar perfil local (JSON)") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Snack", Modifier.weight(1f))
            FilterChip(selected = state.settings.snackTiming == "Mañana", onClick = { run { store.update { it.copy(settings = it.settings.copy(snackTiming = "Mañana")) } } }, label = { Text("Mañana") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = state.settings.snackTiming == "Merienda", onClick = { run { store.update { it.copy(settings = it.settings.copy(snackTiming = "Merienda")) } } }, label = { Text("Merienda") })
        }
    }
    PanelCard("Pasos y sincronización") {
        Text("Selecciona una única fuente para las próximas sesiones. El contador empieza con la sesión; el total previo del día no cuenta.", color = Muted)
        listOf("health_connect" to "Health Connect · preferida", "sensor" to "Sensor del móvil", "none" to "Sin fuente por ahora").forEach { (source,label) ->
            RadioRow(label, state.settings.stepSource == source) { run { store.update { it.copy(settings = it.settings.copy(stepSource = source)) } } }
        }
        if(state.activeSession != null) Text("La fuente de la sesión actual permanece fijada: ${state.activeSession.steps.source}.", color = Lime, style = MaterialTheme.typography.bodySmall)
        Text("Health Connect: ${if(p.healthAvailable) "disponible" else "no disponible / requiere actualización"} · Sensor: ${if(p.phoneSensor) "disponible" else "no disponible"}", color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { if(p.healthAvailable) onHealth() else platform.openHealthSettings() }) { Text("Permitir lectura de Health Connect") }
        TextButton(onClick = { platform.openHealthSettings() }) { Text("Abrir ajustes de Health Connect") }
        TextButton(onClick = onRuntime) { Text("Permitir actividad y notificaciones") }
        Text("Mi Fitness debe compartir pasos con Health Connect. La lectura agregada deduplica fuentes, puede llegar tarde y puede prorratear intervalos que cruzan el inicio. No hay conexión directa por Bluetooth.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    PanelCard("Concentración voluntaria") {
        CheckRow("Solicitar concentración al empezar", state.settings.focusEnabled) { checked -> run { store.update { it.copy(settings = it.settings.copy(focusEnabled = checked)) } } }
        Text("Protección ${if(p.accessibilityEnabled && p.focusServiceConnected) "disponible" else "inactiva"}. Solo GymApp y Spotify durante la sesión, además de las superficies esenciales del sistema.", color = if(p.focusServiceConnected) Lime else Muted)
        TextButton(onClick = { platform.openAccessibilitySettings() }) { Text("Configurar Accesibilidad") }
        Text("Accesibilidad detecta el paquete de la app abierta y presenta una barrera; no lee mensajes ni textos. El permiso es revocable. No equivale a un modo Zen del fabricante ni garantiza protección ante reinicio, cierre forzado o desinstalación.", style = MaterialTheme.typography.bodySmall, color = Muted)
        if(state.activeSession != null) Text("Los cambios se aplican a futuras sesiones. La salida de emergencia sigue accesible.", color = Lime, style = MaterialTheme.typography.bodySmall)
    }
    PanelCard("Recordatorios") {
        CheckRow("Avisos de constancia", state.settings.reminderEnabled) { checked -> run { store.update { it.copy(settings = it.settings.copy(reminderEnabled = checked)) } } }
        Text("Tras tres días naturales completos sin pesas: cada ${state.settings.reminderIntervalMinutes} min, ${state.settings.reminderStart}–${state.settings.reminderEnd}.", color = Muted)
        Text("Notificaciones: ${if(p.notifications) "permitidas" else "sin permiso"}", color = Muted)
        state.settings.snoozeUntil?.takeIf { it > System.currentTimeMillis() }?.let { Text("Pospuesto hasta ${it.clock()}", color = Lime) }
        if(state.settings.pauseFrom != null) Text("Pausa: ${state.settings.pauseFrom} → ${state.settings.pauseUntil}", color = Lime)
        TextButton(onClick = { reminders = true }) { Text("Horario, intervalo y pausa con fechas") }
        TextButton(onClick = { run { store.update { it.copy(settings = it.settings.copy(snoozeUntil = System.currentTimeMillis() + 30*60_000)) } } }) { Text("Posponer 30 minutos") }
        TextButton(onClick = { platform.openNotificationSettings() }) { Text("Ajustes de notificaciones") }
        TextButton(onClick = { platform.openBatterySettings() }) { Text("Batería / configuración de HyperOS") }
        Text("Entrega aproximada sujeta a Doze, batería, silencio y No molestar. En HyperOS comprueba inicio automático, Accesibilidad y batería en Ajustes. Los menús dependen de la versión instalada.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    PanelCard("Copia y privacidad") {
        MainButton("Exportar copia", onExport)
        OutlinedButton(onClick = onImport, enabled = state.activeSession == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Restaurar copia") }
        if(state.activeSession != null) Text("Termina la sesión antes de restaurar datos.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Copia JSON versionada con perfil, registros y preferencias. La restauración valida formato y referencias antes de reemplazar los datos.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(store.backupError ?: "Copia automática local rotatoria activada (tres posiciones). Exporta fuera de la app para protegerte ante desinstalación o pérdida del teléfono.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Sin cuentas, publicidad, analítica ni IA en funcionamiento. La app no añade calorías del reloj al objetivo de comida.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if(goals) GoalsEditor(state.settings, { goals = false }) { settings -> run { store.update { it.copy(settings = settings) }; goals = false } }
    if(reminders) ReminderEditor(state.settings, { reminders = false }) { settings -> run { store.update { it.copy(settings = settings) }; reminders = false } }
    if(profile) ProfileEditor(state.profile, { profile = false }) { edited -> run { store.update { it.copy(profile = edited) }; profile = false } }
}

@Composable
private fun GoalsEditor(initial: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var energy by remember { mutableStateOf(initial.energyTarget.toString()) }
    var protein by remember { mutableStateOf(initial.proteinTarget.toString()) }
    var stepGoal by remember { mutableStateOf(initial.stepGoal.toString()) }
    var increment by remember { mutableStateOf(initial.equipmentIncrement.toString()) }
    var timed by remember { mutableStateOf(initial.timedCardioEnabled) }
    var minutes by remember { mutableStateOf(initial.timedCardioMinutes.toString()) }
    val valid = energy.toIntOrNull() in 1000..6000 && protein.toIntOrNull() in 20..500 && stepGoal.toIntOrNull() in 100..30000 && increment.number()?.let { it.isFinite() && it in 0.1..100.0 } == true && minutes.toIntOrNull() in 1..240
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Objetivos y material") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(energy, { energy = it }, "Objetivo de energía (kcal/día)")
            NumberField(protein, { protein = it }, "Objetivo de proteína (g/día)")
            NumberField(stepGoal, { stepGoal = it }, "Pasos desde inicio de sesión")
            NumberField(increment, { increment = it }, "Incremento disponible de carga (kg)")
            CheckRow("Permitir elegir cardio por tiempo", timed) { timed = it }
            NumberField(minutes, { minutes = it }, "Objetivo alternativo de cardio (minutos)")
            Text("El cardio por tiempo debe elegirse expresamente en cada sesión. No representa una equivalencia con los pasos. Los cambios de pasos y concentración se aplican al siguiente inicio.", color = Muted)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(initial.copy(energyTarget = energy.toInt(), proteinTarget = protein.toInt(), stepGoal = stepGoal.toInt(), equipmentIncrement = increment.number()!!, timedCardioEnabled = timed, timedCardioMinutes = minutes.toInt())) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ReminderEditor(initial: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var start by remember { mutableStateOf(initial.reminderStart) }
    var end by remember { mutableStateOf(initial.reminderEnd) }
    var interval by remember { mutableStateOf(initial.reminderIntervalMinutes.toString()) }
    var from by remember { mutableStateOf(initial.pauseFrom ?: "") }
    var until by remember { mutableStateOf(initial.pauseUntil ?: "") }
    val timeValid = runCatching { java.time.LocalTime.parse(start).isBefore(java.time.LocalTime.parse(end)) }.getOrDefault(false)
    val pauseValid = (from.isBlank() && until.isBlank()) || runCatching { !LocalDate.parse(until).isBefore(LocalDate.parse(from)) }.getOrDefault(false)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Horario y pausa") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(start, { start = it }, label = { Text("Desde · HH:mm") }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("Hasta · HH:mm") }, singleLine = true)
            NumberField(interval, { interval = it }, "Intervalo (minutos)")
            Text("Pausa explícita por enfermedad, lesión o vacaciones. Deja ambas fechas vacías para quitarla.", color = Muted)
            OutlinedTextField(from, { from = it }, label = { Text("Inicio pausa · AAAA-MM-DD") }, singleLine = true)
            OutlinedTextField(until, { until = it }, label = { Text("Fin pausa · AAAA-MM-DD") }, singleLine = true)
            if(!timeValid || !pauseValid) Text("Comprueba horarios y fechas. El final debe ser posterior al inicio.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = timeValid && pauseValid && interval.toIntOrNull() in 15..360, onClick = { onSave(initial.copy(reminderStart = start, reminderEnd = end, reminderIntervalMinutes = interval.toInt(), pauseFrom = from.ifBlank { null }, pauseUntil = until.ifBlank { null })) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ProfileEditor(initial: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var age by remember { mutableStateOf(initial.age?.toString() ?: "") }
    var height by remember { mutableStateOf(initial.heightCm?.toString() ?: "") }
    var current by remember { mutableStateOf(initial.currentWeight?.toString() ?: "") }
    var target by remember { mutableStateOf(initial.targetWeight?.toString() ?: "") }
    var wearable by remember { mutableStateOf(initial.wearableModel ?: "") }
    val valid = (age.isBlank() || age.toIntOrNull() in 18..120) && (height.isBlank() || height.number()?.let { it.isFinite() && it in 100.0..250.0 } == true) && listOf(current,target).all { value -> value.isBlank() || value.number()?.let { it.isFinite() && it in 20.0..500.0 } == true }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tu perfil · opcional") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(age, { age = it }, "Edad (años)")
            NumberField(height, { height = it }, "Estatura (cm)")
            NumberField(current, { current = it }, "Peso actual (kg)")
            NumberField(target, { target = it }, "Peso objetivo (kg)")
            OutlinedTextField(wearable, { if(it.length <= 150) wearable = it }, label = { Text("Modelo de reloj · si lo conoces") })
            Text("El perfil no cambia automáticamente tus calorías ni demuestra que el reloj esté conectado.", color = Muted)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(Profile(age = age.toIntOrNull(), heightCm = height.number(), currentWeight = current.number(), targetWeight = target.number(), wearableModel = wearable.ifBlank { null })) }) { Text("Guardar perfil") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
