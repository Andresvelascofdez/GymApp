package com.gymapp.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable data class Nutrition(
    @SerialName("energy_kcal") val kcal: Double = 0.0,
    @SerialName("protein_g") val protein: Double = 0.0,
    @SerialName("fat_g") val fat: Double = 0.0,
    @SerialName("carbohydrates_available_g") val carbs: Double = 0.0,
    @SerialName("fiber_g") val fiber: Double = 0.0
) {
    operator fun plus(other: Nutrition) = Nutrition(kcal + other.kcal, protein + other.protein, fat + other.fat, carbs + other.carbs, fiber + other.fiber)
    operator fun times(factor: Double) = Nutrition(kcal * factor, protein * factor, fat * factor, carbs * factor, fiber * factor)
}

@Serializable data class FoodVersion(
    val id: String,
    @SerialName("name_es") val name: String,
    val version: Int = 1,
    @SerialName("reference_g") val referenceGrams: Double = 100.0,
    @SerialName("weight_basis") val weightBasis: String = "as_sold",
    @SerialName("energy_kcal") val kcal: Double = 0.0,
    @SerialName("protein_g") val protein: Double = 0.0,
    @SerialName("fat_g") val fat: Double = 0.0,
    @SerialName("carbohydrates_available_g") val carbs: Double = 0.0,
    @SerialName("fiber_g") val fiber: Double = 0.0,
    @SerialName("source_id") val source: String = "user_label",
    @SerialName("source_record") val sourceRecord: String? = null,
    @SerialName("source_carbohydrate_basis") val carbohydrateBasis: String = "available"
) {
    val nutrition: Nutrition get() = Nutrition(kcal, protein, fat, carbs, fiber)
}
@Serializable data class Ingredient(@SerialName("food_id") val foodId: String, val grams: Double, val foodVersion: Int? = null)
@Serializable data class RecipeVersion(
    val id: String, val version: Int = 1,
    @SerialName("name_es") val name: String,
    val slot: String = "snack", val servings: Double = 1.0,
    val ingredients: List<Ingredient>,
    @SerialName("instructions_es") val instructions: List<String> = emptyList(),
    @SerialName("storage_note_es") val storageNote: String = "",
    @SerialName("nutrition_reference") val reference: Nutrition? = null
)
@Serializable data class PlanDay(val weekday: String, @SerialName("name_es") val name: String, val meals: Map<String, String>, @SerialName("nutrition_reference") val reference: Nutrition? = null)
@Serializable data class Exercise(val id: String, @SerialName("name_es") val name: String, val alternatives: List<String> = emptyList(), @SerialName("load_unit") val loadUnit: String = "kg", @SerialName("per_side_repetitions") val perSide: Boolean = false)
@Serializable data class PlannedExercise(@SerialName("exercise_id") val exerciseId: String, val sets: Int, @SerialName("reps_min") val repsMin: Int, @SerialName("reps_max") val repsMax: Int, @SerialName("rest_seconds") val restSeconds: Int, @SerialName("target_reps_in_reserve") val targetRir: Int = 2)
@Serializable data class RoutineDay(val id: String, @SerialName("name_es") val name: String, val weekday: String = "", val exercises: List<PlannedExercise>)
@Serializable data class Catalog(val foods: List<FoodVersion>, val recipes: List<RecipeVersion>, val days: List<PlanDay>, val exercises: List<Exercise>, val routines: List<RoutineDay>)

@Serializable data class Profile(
    @SerialName("age_years") val age: Int? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("current_weight_kg") val currentWeight: Double? = null,
    @SerialName("target_weight_kg") val targetWeight: Double? = null,
    @SerialName("wearable_model") val wearableModel: String? = null
)
@Serializable data class Settings(
    val energyTarget: Int = 2200, val proteinTarget: Int = 200,
    val snackTiming: String = "Merienda", val focusEnabled: Boolean = true,
    val stepSource: String = "none", val stepGoal: Int = 3500,
    val timedCardioEnabled: Boolean = false, val timedCardioMinutes: Int = 20,
    val reminderEnabled: Boolean = true, val reminderIntervalMinutes: Int = 60,
    val reminderStart: String = "06:00", val reminderEnd: String = "21:00",
    val pauseFrom: String? = null, val pauseUntil: String? = null,
    val snoozeUntil: Long? = null, val lastReminderAt: Long? = null,
    val equipmentIncrement: Double = 2.5
)
@Serializable data class IngredientSnapshot(val food: FoodVersion, val grams: Double)
@Serializable data class MealLog(val id: String = newId(), val date: String, val slot: String, val name: String, val recipeId: String? = null, val recipeVersion: Int? = null, val ingredients: List<IngredientSnapshot>, val nutrition: Nutrition, val plannedKey: String? = null)
@Serializable data class MealOverride(val date: String, val slot: String, val recipeId: String, val portions: Double = 1.0, val grams: Map<String, Double> = emptyMap(), val recipeVersion: Int? = null)
@Serializable data class ShoppingItem(val foodId: String, val name: String, val grams: Double, val checked: Boolean = false)
@Serializable data class SetLog(val id: String = newId(), val exerciseId: String, val index: Int, val weight: Double, val reps: Int, val warmup: Boolean = false, val rir: Int? = null, val note: String = "", val techniqueGood: Boolean = true)
@Serializable data class StepState(val source: String = "none", val count: Long = 0, val baseline: Long? = null, val lastRaw: Long? = null, val bootId: String? = null, val lastReadAt: Long? = null, val status: String = "Pendiente de sincronizar")
@Serializable enum class SessionStatus { WEIGHTS_ACTIVE, CARDIO_PENDING, COMPLETED, ABANDONED, EMERGENCY_EXIT, SENSOR_UNVERIFIED }
@Serializable data class WorkoutSession(
    val id: String = newId(), val startedAt: Long = Instant.now().toEpochMilli(),
    val zoneId: String = ZoneId.systemDefault().id, val routineId: String,
    val plan: List<PlannedExercise>, val abbreviated: Boolean = false,
    val sets: List<SetLog> = emptyList(), val status: SessionStatus = SessionStatus.WEIGHTS_ACTIVE,
    val steps: StepState = StepState(), val stepGoal: Int = 3500,
    val focusRequested: Boolean = true, val weightsCompletedAt: Long? = null,
    val endedAt: Long? = null, val exitReason: String = "",
    val restEndsAt: Long? = null, val timedCardioMinutes: Int? = null, val cardioStartedAt: Long? = null,
    val abandonmentConfirmations: Int = 0
) {
    val workSets get() = sets.filterNot { it.warmup }
    val active get() = status == SessionStatus.WEIGHTS_ACTIVE || status == SessionStatus.CARDIO_PENDING
    val stepsRemaining get() = (stepGoal - steps.count).coerceAtLeast(0)
}
@Serializable data class WeightLog(val id: String = newId(), val date: String, val kg: Double)
@Serializable data class WellbeingLog(val date: String, val hunger: Int? = null, val energy: Int? = null, val performance: Int? = null, val note: String = "")
@Serializable data class AppState(
    val schemaVersion: Int = 1, val enrolledOn: String = LocalDate.now().toString(),
    val profile: Profile = Profile(), val settings: Settings = Settings(),
    val activeSession: WorkoutSession? = null, val workouts: List<WorkoutSession> = emptyList(),
    val nextRoutineIndex: Int = 0, val mealLogs: List<MealLog> = emptyList(),
    val mealOverrides: List<MealOverride> = emptyList(), val customFoods: List<FoodVersion> = emptyList(),
    val customRecipes: List<RecipeVersion> = emptyList(), val routineOverrides: List<RoutineDay> = emptyList(),
    val favoriteRecipes: Set<String> = emptySet(), val shopping: List<ShoppingItem> = emptyList(),
    val weights: List<WeightLog> = emptyList(), val wellbeing: List<WellbeingLog> = emptyList()
)
@Serializable data class BackupEnvelope(val format: String = "GymApp", val version: Int = 1, val exportedAt: Long = Instant.now().toEpochMilli(), val state: AppState)
