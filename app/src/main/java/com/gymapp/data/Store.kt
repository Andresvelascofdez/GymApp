package com.gymapp.data

import android.content.Context
import android.util.AtomicFile
import androidx.room.*
import com.gymapp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@Entity(tableName = "app_state") data class StateEntity(@PrimaryKey val id: Int = 1, val payload: String)
@Dao interface StateDao {
    @Query("SELECT * FROM app_state WHERE id = 1") fun load(): StateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(value: StateEntity)
}
@Database(entities = [StateEntity::class], version = 1, exportSchema = true)
abstract class GymDatabase : RoomDatabase() { abstract fun stateDao(): StateDao }

@Serializable private data class FoodsAsset(val foods: List<FoodVersion>)
@Serializable private data class RecipesAsset(val recipes: List<RecipeVersion>)
@Serializable private data class PlanAsset(val days: List<PlanDay>)
@Serializable private data class RoutineAsset(val exercises: List<Exercise>, val sessions: List<RoutineDay>)

class Store private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
    }
    private val json = BackupCodec.json
    private inline fun <reified T> asset(path: String): T = json.decodeFromString(context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() })
    private val routineAsset = asset<RoutineAsset>("training/routine.json")
    val catalog = Catalog(asset<FoodsAsset>("nutrition/foods.json").foods, asset<RecipesAsset>("nutrition/recipes.json").recipes, asset<PlanAsset>("nutrition/week_plan.json").days, routineAsset.exercises, routineAsset.sessions)
    private val database = Room.databaseBuilder(context, GymDatabase::class.java, "gymapp.db").build()
    private val mutex = Any()
    private val mutable = MutableStateFlow(runBlocking(Dispatchers.IO) {
        database.stateDao().load()?.let { BackupCodec.decode(it.payload, catalog) } ?: AppState(profile = initialProfile()).also { database.stateDao().save(StateEntity(payload = BackupCodec.encode(it))) }
    })
    val state: StateFlow<AppState> = mutable
    @Volatile var backupError: String? = null
        private set
    val allFoods get() = (catalog.foods + state.value.customFoods).groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }
    val allRecipes get() = (catalog.recipes + state.value.customRecipes).groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }
    val routines get() = catalog.routines.map { original -> state.value.routineOverrides.lastOrNull { it.id == original.id } ?: original }
    private fun initialProfile(): Profile = try { asset<Profile>("profile.personal.json") } catch (_: java.io.FileNotFoundException) { Profile() }

    fun update(transform: (AppState) -> AppState) = synchronized(mutex) {
        val next = transform(mutable.value)
        if (next == mutable.value) return@synchronized
        BackupCodec.validate(next, catalog)
        val payload = BackupCodec.encode(next)
        require(payload.toByteArray(Charsets.UTF_8).size <= BackupCodec.MAX_BYTES) { "Los datos superan el tamaño máximo de copia (8 MB)" }
        runBlocking(Dispatchers.IO) {
            database.runInTransaction { database.stateDao().save(StateEntity(payload = payload)) }
        }
        mutable.value = next
        runBlocking(Dispatchers.IO) {
            try { writeLocalBackup(payload); backupError = null } catch (_: java.io.IOException) { backupError = "No se pudo actualizar la copia rotatoria; exporta una copia manual" }
        }
    }

    private fun writeLocalBackup(payload: String) {
        val folder = File(context.noBackupFilesDir, "local_backups").apply { mkdirs() }
        val slot = (System.currentTimeMillis() / 86_400_000L % 3).toInt()
        val file = AtomicFile(File(folder, "backup-$slot.json"))
        val stream = file.startWrite()
        try { stream.write(payload.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun exportJson(): String = BackupCodec.encode(state.value)
    fun importJson(text: String) {
        val replacement = BackupCodec.decode(text, catalog)
        val hasTraining = (replacement.workouts + listOfNotNull(replacement.activeSession)).any { it.weightsCompletedAt != null && it.workSets.isNotEmpty() }
        update { if (hasTraining) replacement else replacement.copy(enrolledOn = LocalDate.now().toString()) }
    }
    fun importProfileJson(text: String) {
        require(text.toByteArray().size < 100_000)
        val profile = json.decodeFromString<Profile>(text)
        update { it.copy(profile = profile) }
    }

    fun startSession(abbreviated: Boolean = false) = update { current ->
        require(current.activeSession == null) { "Ya hay una sesiÃ³n pendiente" }
        val routine = routines[current.nextRoutineIndex]
        val plan = if (abbreviated) routine.exercises.take(3).map { it.copy(sets = 2) } else routine.exercises
        current.copy(activeSession = WorkoutSession(routineId = routine.id, plan = plan, abbreviated = abbreviated, steps = StepState(source = current.settings.stepSource), stepGoal = current.settings.stepGoal, focusRequested = current.settings.focusEnabled))
    }
    private fun changeSession(transform: (WorkoutSession) -> WorkoutSession) = update { state -> state.copy(activeSession = transform(requireNotNull(state.activeSession) { "No hay sesiÃ³n activa" })) }
    fun saveSet(set: SetLog) = changeSession { session ->
        require(session.status == SessionStatus.WEIGHTS_ACTIVE)
        require(session.plan.any { it.exerciseId == set.exerciseId })
        session.copy(sets = session.sets.filterNot { it.id == set.id || (it.exerciseId == set.exerciseId && it.index == set.index && it.warmup == set.warmup) } + set)
    }
    fun deleteSet(id: String) = changeSession { it.copy(sets = it.sets.filterNot { s -> s.id == id }) }
    fun replaceExercise(oldId: String, newId: String) = changeSession { session ->
        require(catalog.exercises.any { it.id == newId })
        require(session.sets.none { it.exerciseId == oldId }) { "Conserva el ejercicio con series o elimina antes esas series" }
        session.copy(plan = session.plan.map { if (it.exerciseId == oldId) it.copy(exerciseId = newId) else it })
    }
    fun previousSets(exerciseId: String): List<SetLog> = state.value.workouts.asReversed().firstOrNull { it.sets.any { set -> set.exerciseId == exerciseId && !set.warmup } }?.sets?.filter { it.exerciseId == exerciseId } ?: emptyList()
    fun finishWeights(reason: String = "") = update { current ->
        val session = requireNotNull(current.activeSession)
        require(session.status == SessionStatus.WEIGHTS_ACTIVE && session.workSets.isNotEmpty()) { "Registra al menos una serie efectiva" }
        val omitted = session.plan.any { p -> session.workSets.count { it.exerciseId == p.exerciseId } < p.sets }
        require(!omitted || reason.isNotBlank()) { "Confirma los ejercicios/series omitidos y explica el motivo" }
        val now = Instant.now().toEpochMilli()
        current.copy(activeSession = session.copy(status = SessionStatus.CARDIO_PENDING, weightsCompletedAt = now, exitReason = reason, restEndsAt = null), nextRoutineIndex = (current.nextRoutineIndex + 1) % routines.size, settings = current.settings.copy(snoozeUntil = null))
    }
    fun closeSession(status: SessionStatus, reason: String = "") = update { current ->
        val session = requireNotNull(current.activeSession)
        require(status !in setOf(SessionStatus.WEIGHTS_ACTIVE, SessionStatus.CARDIO_PENDING))
        if (status == SessionStatus.COMPLETED) {
            require(session.weightsCompletedAt != null && session.workSets.isNotEmpty())
            val timedComplete = session.timedCardioMinutes?.let { minutes -> session.cardioStartedAt?.let { Instant.now().toEpochMilli() - it >= minutes * 60_000L } } == true
            val verifiedSteps = session.steps.source != "none" && session.steps.lastReadAt != null && session.steps.count >= session.stepGoal && !session.steps.status.contains("sin verificar")
            require(verifiedSteps || timedComplete) { "Objetivo pendiente o sin lectura verificable; puedes usar la salida de fallo de sensor" }
        } else require(reason.isNotBlank()) { "Indica el motivo de salida" }
        if (status == SessionStatus.ABANDONED) require(session.abandonmentConfirmations >= 9) { "Completa las diez confirmaciones" }
        current.copy(activeSession = null, workouts = current.workouts + session.copy(status = status, endedAt = Instant.now().toEpochMilli(), exitReason = reason.ifBlank { session.exitReason }, restEndsAt = null))
    }
    fun confirmAbandon(): Boolean {
        val confirmations = state.value.activeSession?.abandonmentConfirmations ?: return false
        if (confirmations >= 9) { closeSession(SessionStatus.ABANDONED, "Abandono confirmado en diez pasos"); return true }
        changeSession { it.copy(abandonmentConfirmations = confirmations + 1) }
        return false
    }
    fun cancelAbandon() = changeSession { it.copy(abandonmentConfirmations = 0) }
    fun startRest(seconds: Int) { require(seconds in 0..3600); changeSession { it.copy(restEndsAt = System.currentTimeMillis() + seconds * 1000L) } }
    fun chooseTimedCardio() {
        require(state.value.settings.timedCardioEnabled) { "Activa primero el objetivo alternativo" }
        changeSession { require(it.status == SessionStatus.CARDIO_PENDING); it.copy(timedCardioMinutes = state.value.settings.timedCardioMinutes, cardioStartedAt = System.currentTimeMillis()) }
    }
    fun updateSessionSteps(sessionId: String, count: Long, lastReadAt: Long, status: String, source: String) = update { current ->
        val session = current.activeSession
        if (session == null || session.id != sessionId || session.steps.source != source || lastReadAt < session.startedAt || lastReadAt < (session.steps.lastReadAt ?: 0)) current
        else current.copy(activeSession = session.copy(steps = session.steps.copy(count = count.coerceAtLeast(0), lastReadAt = lastReadAt, status = status)))
    }
    fun sensorReading(sessionId: String, raw: Long, bootId: String, at: Long) = update { current ->
        val session = current.activeSession
        if (session == null || session.id != sessionId || session.steps.source != "sensor" || at < session.startedAt) current
        else current.copy(activeSession = session.copy(steps = Rules.sensorReading(session.steps, raw, bootId, at)))
    }

    fun planFor(date: LocalDate): List<Pair<String, RecipeVersion>> = planFor(date, state.value)

    private fun planFor(date: LocalDate, current: AppState): List<Pair<String, RecipeVersion>> {
        val day = catalog.days.firstOrNull { it.weekday == date.dayOfWeek.name.lowercase(Locale.ROOT) }
        val overrides = current.mealOverrides.filter { it.date == date.toString() }
        val slots = (day?.meals?.keys.orEmpty() + overrides.map { it.slot }).distinct()
        return slots.mapNotNull { slot ->
            val id = overrides.firstOrNull { it.slot == slot }?.recipeId ?: day?.meals?.get(slot)
            val version = overrides.firstOrNull { it.slot == slot }?.recipeVersion
            (catalog.recipes + current.customRecipes).filter { it.id == id && (version == null || it.version == version) }.maxByOrNull { it.version }?.let { slot to it }
        }
    }
    fun mealSnapshot(date: LocalDate, slot: String): MealLog = mealSnapshot(date, slot, state.value)

    private fun mealSnapshot(date: LocalDate, slot: String, current: AppState): MealLog {
        val recipe = requireNotNull(planFor(date, current).firstOrNull { it.first == slot }?.second)
        val override = current.mealOverrides.firstOrNull { it.date == date.toString() && it.slot == slot }
        val portions = override?.portions ?: 1.0
        val ingredients = recipe.ingredients.map { ingredient ->
            val food = (catalog.foods + current.customFoods).filter { it.id == ingredient.foodId && (ingredient.foodVersion == null || ingredient.foodVersion == it.version) }.maxBy { it.version }
            IngredientSnapshot(food, (override?.grams?.get(ingredient.foodId) ?: ingredient.grams) * portions / recipe.servings)
        }
        return MealLog(date = date.toString(), slot = slot, name = recipe.name, recipeId = recipe.id, recipeVersion = recipe.version, ingredients = ingredients, nutrition = Rules.nutrition(ingredients), plannedKey = "$date:$slot")
    }
    fun toggleMeal(date: LocalDate, slot: String) {
        val key = "$date:$slot"
        update { current ->
            if (current.mealLogs.any { it.plannedKey == key }) current.copy(mealLogs = current.mealLogs.filterNot { it.plannedKey == key })
            else current.copy(mealLogs = current.mealLogs + mealSnapshot(date, slot))
        }
    }
    fun setMealConsumed(date: LocalDate, slot: String, consumed: Boolean) = update { current ->
        val key = "$date:$slot"
        if (!consumed) current.copy(mealLogs = current.mealLogs.filterNot { it.plannedKey == key })
        else if (current.mealLogs.any { it.plannedKey == key }) current else current.copy(mealLogs = current.mealLogs + mealSnapshot(date, slot))
    }
    fun setMealOverride(override: MealOverride) = update { current ->
        val changed = current.copy(mealOverrides = current.mealOverrides.filterNot { it.date == override.date && it.slot == override.slot } + override)
        changed
    }
    fun refreshConsumedMeal(date: LocalDate, slot: String) {
        val snapshot = mealSnapshot(date, slot)
        update { it.copy(mealLogs = it.mealLogs.filterNot { meal -> meal.plannedKey == snapshot.plannedKey } + snapshot) }
    }
    fun logFood(date: LocalDate, slot: String, foodId: String, grams: Double) {
        val food = allFoods.first { it.id == foodId }
        val ingredients = listOf(IngredientSnapshot(food, grams))
        val meal = MealLog(date = date.toString(), slot = slot, name = food.name, ingredients = ingredients, nutrition = Rules.nutrition(ingredients))
        update { it.copy(mealLogs = it.mealLogs + meal) }
    }
    fun deleteMeal(id: String) = update { it.copy(mealLogs = it.mealLogs.filterNot { m -> m.id == id }) }
    fun saveFood(food: FoodVersion) = update { current ->
        val version = (catalog.foods + current.customFoods).filter { it.id == food.id }.maxOfOrNull { it.version }?.plus(1) ?: 1
        current.copy(customFoods = current.customFoods + food.copy(version = version))
    }
    private fun withRecipeVersion(current: AppState, recipe: RecipeVersion): AppState {
        val previousVersion = (catalog.recipes + current.customRecipes).filter { it.id == recipe.id }.maxOfOrNull { it.version }
        val overrides = current.mealOverrides.map { item ->
            if (item.recipeId == recipe.id && item.recipeVersion == null) item.copy(recipeVersion = previousVersion) else item
        }
        return current.copy(customRecipes = current.customRecipes + recipe.copy(version = (previousVersion ?: 0) + 1), mealOverrides = overrides)
    }
    fun saveRecipe(recipe: RecipeVersion) = update { current -> withRecipeVersion(current, recipe) }
    fun saveMealEdit(recipe: RecipeVersion, override: MealOverride, updateConsumed: Boolean) = update { current ->
        require(recipe.id == override.recipeId)
        val revised = withRecipeVersion(current, recipe)
        val version = revised.customRecipes.last().version
        var changed = revised.copy(mealOverrides = revised.mealOverrides.filterNot { it.date == override.date && it.slot == override.slot } + override.copy(recipeVersion = version))
        if (updateConsumed) {
            val snapshot = mealSnapshot(LocalDate.parse(override.date), override.slot, changed)
            val previous = current.mealLogs.firstOrNull { it.plannedKey == snapshot.plannedKey }
            if (previous != null) changed = changed.copy(mealLogs = changed.mealLogs.filterNot { it.plannedKey == snapshot.plannedKey } + snapshot.copy(id = previous.id))
        }
        changed
    }
    fun repeatMeal(meal: MealLog, date: LocalDate) = update { it.copy(mealLogs = it.mealLogs + meal.copy(id = newId(), date = date.toString(), plannedKey = null)) }
    fun buildShopping(dates: List<LocalDate>, portions: Double = 1.0) {
        require(portions in 0.01..100.0)
        val ingredients = dates.flatMap { date -> planFor(date).flatMap { (slot, _) -> mealSnapshot(date, slot).ingredients } }
        val items = ingredients.groupBy { it.food.id }.map { (id, list) -> ShoppingItem(id, list.first().food.name, list.sumOf { it.grams } * portions) }
        update { it.copy(shopping = items) }
    }
    fun saveWeight(date: LocalDate, kg: Double) = update { it.copy(weights = it.weights.filterNot { w -> w.date == date.toString() } + WeightLog(date = date.toString(), kg = kg)) }
    fun saveWellbeing(log: WellbeingLog) = update { it.copy(wellbeing = it.wellbeing.filterNot { w -> w.date == log.date } + log) }
}

