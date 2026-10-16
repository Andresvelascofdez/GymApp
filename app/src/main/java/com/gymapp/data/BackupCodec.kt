package com.gymapp.data

import com.gymapp.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = false; allowSpecialFloatingPointValues = false }
    fun encode(state: AppState): String = json.encodeToString(BackupEnvelope(state = state))
    fun decode(text: String, catalog: Catalog): AppState {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Copia demasiado grande (máximo 8 MB)" }
        val envelope = json.decodeFromString<BackupEnvelope>(text)
        require(envelope.format == "GymApp" && envelope.version == 1) { "Formato o versión no compatible" }
        validate(envelope.state, catalog)
        return envelope.state
    }
    fun validate(state: AppState, catalog: Catalog) {
        require(state.schemaVersion == 1)
        LocalDate.parse(state.enrolledOn)
        require(state.nextRoutineIndex in catalog.routines.indices)
        val s = state.settings
        require(s.energyTarget in 500..10000 && s.proteinTarget in 0..500 && s.stepGoal in 1..100000)
        require(s.timedCardioMinutes in 1..600 && s.reminderIntervalMinutes in 15..1440 && s.equipmentIncrement in 0.01..100.0)
        require(s.stepSource in setOf("none", "sensor", "health_connect"))
        LocalTime.parse(s.reminderStart); LocalTime.parse(s.reminderEnd)
        require((s.pauseFrom == null) == (s.pauseUntil == null))
        if (s.pauseFrom != null) require(LocalDate.parse(s.pauseFrom) <= LocalDate.parse(s.pauseUntil))
        state.profile.let { p ->
            require(p.age == null || p.age in 1..120)
            require(p.heightCm == null || p.heightCm in 50.0..260.0)
            require(p.currentWeight == null || p.currentWeight in 15.0..500.0)
            require(p.targetWeight == null || p.targetWeight in 15.0..500.0)
        }
        fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() && it.length <= 200 } && ids.distinct().size == ids.size) { "Identificadores duplicados o inválidos" } }
        val foods = catalog.foods + state.customFoods
        val recipes = catalog.recipes + state.customRecipes
        unique(foods.map { "${it.id}:${it.version}" }); unique(recipes.map { "${it.id}:${it.version}" })
        require((foods.map { it.id } + recipes.map { it.id }).all { it.isNotBlank() && it.length <= 180 })
        fun validNutrition(n: Nutrition) = listOf(n.kcal, n.protein, n.fat, n.carbs, n.fiber).all { it.isFinite() && it in 0.0..1_000_000.0 }
        foods.forEach { require(it.version > 0 && it.name.isNotBlank() && it.referenceGrams > 0 && validNutrition(it.nutrition)) }
        recipes.forEach { recipe ->
            require(recipe.version > 0 && recipe.servings > 0 && recipe.servings.isFinite() && recipe.ingredients.isNotEmpty())
            recipe.ingredients.forEach { ingredient -> require(ingredient.grams.isFinite() && ingredient.grams in 0.0..100000.0 && foods.any { it.id == ingredient.foodId && (ingredient.foodVersion == null || it.version == ingredient.foodVersion) }) }
        }
        val exerciseIds = catalog.exercises.map { it.id }.toSet()
        unique(state.routineOverrides.map { it.id })
        (catalog.routines + state.routineOverrides).forEach { day ->
            require(day.id in catalog.routines.map { it.id } && day.exercises.isNotEmpty())
            unique(day.exercises.map { it.exerciseId })
            day.exercises.forEach { require(it.exerciseId in exerciseIds && it.sets in 1..20 && it.repsMin in 1..100 && it.repsMax in it.repsMin..100 && it.restSeconds in 0..3600 && it.targetRir in 0..10) }
        }
        unique(state.workouts.map { it.id } + listOfNotNull(state.activeSession?.id))
        (state.workouts + listOfNotNull(state.activeSession)).forEach { session ->
            require(session.startedAt > 0 && session.routineId in catalog.routines.map { it.id }); ZoneId.of(session.zoneId)
            require(session.steps.count in 0..1_000_000 && session.steps.source in setOf("none", "sensor", "health_connect"))
            require(session.abandonmentConfirmations in 0..10 && session.stepGoal in 1..100000)
            require(session.steps.baseline == null || session.steps.baseline >= 0)
            require(session.steps.lastRaw == null || session.steps.lastRaw >= 0)
            require(session.steps.lastReadAt == null || session.steps.lastReadAt >= session.startedAt)
            require(session.steps.count == 0L || session.steps.source != "none")
            require(session.weightsCompletedAt == null || session.weightsCompletedAt >= session.startedAt)
            require(session.endedAt == null || session.endedAt >= session.startedAt)
            require(session.plan.isNotEmpty())
            unique(session.plan.map { it.exerciseId })
            session.plan.forEach { require(it.exerciseId in exerciseIds && it.sets in 1..20 && it.repsMin in 1..100 && it.repsMax in it.repsMin..100 && it.restSeconds in 0..3600 && it.targetRir in 0..10) }
            unique(session.sets.map { it.id })
            require(session.sets.map { Triple(it.exerciseId, it.index, it.warmup) }.distinct().size == session.sets.size)
            session.sets.forEach { require(it.exerciseId in session.plan.map { p -> p.exerciseId } && it.weight.isFinite() && it.weight in 0.0..2000.0 && it.reps in 1..1000 && it.index in 0..100 && (it.rir == null || it.rir in 0..10)) }
            if (session.weightsCompletedAt != null) require(session.workSets.isNotEmpty())
            if (session.status == SessionStatus.CARDIO_PENDING) require(session.weightsCompletedAt != null)
            if (session.status == SessionStatus.WEIGHTS_ACTIVE) require(session.weightsCompletedAt == null)
            require((session.timedCardioMinutes == null) == (session.cardioStartedAt == null))
            if (session.timedCardioMinutes != null) {
                require(session.timedCardioMinutes in 1..600 && session.weightsCompletedAt != null && session.cardioStartedAt!! >= session.weightsCompletedAt)
                require(session.endedAt == null || session.cardioStartedAt <= session.endedAt)
            }
            if (session.status == SessionStatus.COMPLETED) {
                require(session.weightsCompletedAt != null && session.workSets.isNotEmpty() && session.endedAt != null)
                val timedComplete = session.timedCardioMinutes?.let { minutes -> session.cardioStartedAt?.let { session.endedAt - it >= minutes * 60_000L } } == true
                val verifiedSteps = session.steps.count >= session.stepGoal && session.steps.source != "none" && session.steps.lastReadAt != null && !session.steps.status.contains("sin verificar")
                require(verifiedSteps || timedComplete)
            }
        }
        state.activeSession?.let { require(it.active) }
        require(state.workouts.none { it.active })
        unique(state.mealLogs.map { it.id }); unique(state.mealLogs.mapNotNull { it.plannedKey })
        val slots = setOf("breakfast", "lunch", "dinner", "snack")
        state.mealLogs.forEach { meal ->
            LocalDate.parse(meal.date); require(meal.slot in slots && validNutrition(meal.nutrition) && meal.ingredients.isNotEmpty())
            require(meal.plannedKey == null || meal.plannedKey == "${meal.date}:${meal.slot}")
            meal.ingredients.forEach { require(it.grams.isFinite() && it.grams in 0.0..100000.0 && it.food.referenceGrams > 0 && validNutrition(it.food.nutrition)) }
            require(meal.recipeId == null || recipes.any { it.id == meal.recipeId && it.version == meal.recipeVersion })
            val recalculated = Rules.nutrition(meal.ingredients)
            require(listOf(recalculated.kcal - meal.nutrition.kcal, recalculated.protein - meal.nutrition.protein, recalculated.carbs - meal.nutrition.carbs, recalculated.fat - meal.nutrition.fat, recalculated.fiber - meal.nutrition.fiber).all { kotlin.math.abs(it) < 0.001 }) { "Snapshot nutricional incoherente" }
        }
        require(state.mealOverrides.map { it.date to it.slot }.distinct().size == state.mealOverrides.size)
        state.mealOverrides.forEach { override ->
            LocalDate.parse(override.date)
            val recipe = recipes.filter { it.id == override.recipeId && (override.recipeVersion == null || it.version == override.recipeVersion) }.maxByOrNull { it.version }
            require(recipe != null && override.slot in slots && override.portions.isFinite() && override.portions in 0.01..100.0)
            require(override.grams.all { (id, g) -> recipe.ingredients.any { it.foodId == id } && g.isFinite() && g in 0.0..100000.0 })
        }
        unique(state.weights.map { it.id }); require(state.weights.map { it.date }.distinct().size == state.weights.size)
        state.weights.forEach { LocalDate.parse(it.date); require(it.kg.isFinite() && it.kg in 15.0..500.0) }
        unique(state.wellbeing.map { it.date })
        state.wellbeing.forEach { LocalDate.parse(it.date); require(listOfNotNull(it.hunger, it.energy, it.performance).all { v -> v in 1..5 }) }
        require(state.favoriteRecipes.all { id -> recipes.any { it.id == id } })
        unique(state.shopping.map { it.foodId })
        state.shopping.forEach { require(foods.any { f -> f.id == it.foodId } && it.grams.isFinite() && it.grams in 0.0..1_000_000.0) }
    }
}
