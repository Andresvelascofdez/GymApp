package com.gymapp.domain

import com.gymapp.data.BackupCodec
import kotlinx.serialization.Serializable
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DomainTest {
    @Test fun importedDuplicateRoutineExercisesAndInvalidEffortAreRejected() {
        val routine = catalog.routines.single()
        invalid { BackupCodec.validate(state().copy(routineOverrides = listOf(routine.copy(exercises = routine.exercises + routine.exercises))), catalog) }
        invalid { BackupCodec.validate(state().copy(routineOverrides = listOf(routine.copy(exercises = listOf(plan.copy(targetRir = -1))))), catalog) }
    }
    @Serializable private data class Foods(val foods: List<FoodVersion>)
    @Serializable private data class Recipes(val recipes: List<RecipeVersion>)
    @Serializable private data class Days(val days: List<PlanDay>)
    private val food = FoodVersion("test_food", "Alimento sintético", kcal = 101.0, protein = 10.0, carbs = 12.0, fiber = 3.0)
    private val plan = PlannedExercise("exercise", 2, 8, 12, 120)
    private val catalog = Catalog(listOf(food), listOf(RecipeVersion("recipe", name = "Receta sintética", ingredients = listOf(Ingredient(food.id, 100.0)))), emptyList(), listOf(Exercise("exercise", "Ejercicio sintético")), listOf(RoutineDay("routine", "Rutina sintética", exercises = listOf(plan))))
    private val date = LocalDate.of(2026, 1, 2)
    private val friday = date.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    private fun completed() = WorkoutSession(startedAt = friday - 600_000, routineId = "routine", plan = listOf(plan), sets = listOf(SetLog(exerciseId = "exercise", index = 0, weight = 10.0, reps = 12)), weightsCompletedAt = friday, endedAt = friday + 600_000, status = SessionStatus.COMPLETED, steps = StepState(source = "sensor", count = 3500, lastReadAt = friday + 600_000))
    private fun state() = AppState(enrolledOn = date.toString())
    private fun invalid(action: () -> Unit) { try { action(); fail("Debía rechazar datos inválidos") } catch (_: IllegalArgumentException) { } }

    @Test fun caloriesComeFromSourceAndCarbohydratesAlreadyExcludeFiber() {
        val result = Rules.nutrition(listOf(IngredientSnapshot(food, 250.0)))
        assertEquals(252.5, result.kcal, 0.000001)
        assertEquals(30.0, result.carbs, 0.000001)
        assertEquals(7.5, result.fiber, 0.000001)
        assertNotEquals(result.kcal, result.protein * 4 + result.carbs * 4 + result.fat * 9, 0.01)
    }
    @Test fun snapshotsRetainOldLabel() {
        val snapshot = IngredientSnapshot(food, 100.0)
        val updated = food.copy(version = 2, kcal = 200.0)
        val backup = state().copy(customFoods = listOf(updated), mealLogs = listOf(MealLog(date = date.toString(), slot = "snack", name = "Registro", ingredients = listOf(snapshot), nutrition = Rules.nutrition(listOf(snapshot)))))
        val result = BackupCodec.decode(BackupCodec.encode(backup), catalog)
        assertEquals(101.0, result.mealLogs.single().nutrition.kcal, 0.0)
        assertEquals(1, result.mealLogs.single().ingredients.single().food.version)
    }
    @Test fun allPublicRecipesAndWeekdayReferencesRecalculate() {
        val root = File("../data").takeIf { it.isDirectory } ?: File("data")
        val foods = BackupCodec.json.decodeFromString<Foods>(File(root, "nutrition/foods.json").readText()).foods.associateBy { it.id }
        val recipes = BackupCodec.json.decodeFromString<Recipes>(File(root, "nutrition/recipes.json").readText()).recipes
        val days = BackupCodec.json.decodeFromString<Days>(File(root, "nutrition/week_plan.json").readText()).days
        assertEquals(20, recipes.size); assertEquals(5, days.size)
        val totals = recipes.associate { r ->
            val n = Rules.nutrition(r.ingredients.map { IngredientSnapshot(foods.getValue(it.foodId), it.grams) })
            assertEquals(r.reference!!.kcal, n.kcal, 0.015); assertEquals(r.reference.protein, n.protein, 0.015)
            r.id to n
        }
        days.forEach { day ->
            assertEquals(4, day.meals.size)
            val n = day.meals.values.map { totals.getValue(it) }.fold(Nutrition()) { a, b -> a + b }
            assertEquals(day.reference!!.kcal, n.kcal, 0.02); assertEquals(day.reference.protein, n.protein, 0.02)
            assertTrue(n.kcal in 2000.0..2200.0 && n.protein in 190.0..215.0)
        }
    }
    @Test fun sensorBaselineNeverCountsEarlierDailySteps() {
        val base = Rules.sensorReading(StepState(source = "sensor"), 12000, "boot-a", 1)
        assertEquals(0L, base.count)
        assertEquals(150L, Rules.sensorReading(base, 12150, "boot-a", 2).count)
    }
    @Test fun repeatedSensorSamplesAreIdempotent() {
        val a = Rules.sensorReading(StepState(source = "sensor"), 100, "boot", 1)
        val b = Rules.sensorReading(a, 130, "boot", 2)
        assertEquals(30L, Rules.sensorReading(b, 130, "boot", 3).count)
    }
    @Test fun sensorRebootPreservesCountAndUnverifiedInterval() {
        val a = StepState(source = "sensor", count = 200, baseline = 100, lastRaw = 300, bootId = "a")
        val b = Rules.sensorReading(a, 20, "b", 100)
        assertEquals(200L, b.count); assertTrue(b.status.contains("sin verificar"))
        val c = Rules.sensorReading(b, 30, "b", 200)
        assertEquals(210L, c.count); assertTrue(c.status.contains("sin verificar"))
    }
    @Test fun progressionRequiresEveryRealSetAndTechniqueAndEffort() {
        val logs = (0..1).map { SetLog(exerciseId = "exercise", index = it, weight = 20.0, reps = 12, rir = 2) }
        assertEquals(22.5, Rules.progression(plan, logs, 2.5)!!, 0.0)
        assertNull(Rules.progression(plan, logs.drop(1), 2.5))
        assertNull(Rules.progression(plan, logs.map { it.copy(warmup = true) }, 2.5))
        assertNull(Rules.progression(plan, logs.map { it.copy(techniqueGood = false) }, 2.5))
        assertNull(Rules.progression(plan, logs.map { it.copy(rir = null) }, 2.5))
        assertNull(Rules.progression(plan, logs.map { it.copy(reps = 11) }, 2.5))
        assertNull(Rules.progression(plan, listOf(logs.first().copy(index = 2, reps = 8)) + logs, 2.5))
        assertEquals(22.5, Rules.progression(plan, logs.map { it.copy(weight = 22.0) }, 2.5)!!, 0.0)
    }
    @Test fun absenceFridayFirstReminderTuesday() {
        val s = state().copy(workouts = listOf(completed()))
        val zone = ZoneId.of("UTC")
        assertNull(Rules.reminderKind(s, Instant.parse("2026-01-05T12:00:00Z"), zone))
        assertEquals("absence", Rules.reminderKind(s, Instant.parse("2026-01-06T06:00:00Z"), zone))
        assertNull(Rules.reminderKind(s, Instant.parse("2026-01-06T21:00:00Z"), zone))
    }
    @Test fun noHistoryCountsFromEnrollmentAndAbandonedDoesNotReset() {
        val abandoned = completed().copy(status = SessionStatus.ABANDONED, weightsCompletedAt = null, sets = emptyList())
        val s = state().copy(workouts = listOf(abandoned))
        assertNull(Rules.reminderKind(s, Instant.parse("2026-01-05T12:00:00Z"), ZoneId.of("UTC")))
        assertEquals("absence", Rules.reminderKind(s, Instant.parse("2026-01-06T12:00:00Z"), ZoneId.of("UTC")))
    }
    @Test fun localDatesHandleDstAndZoneChange() {
        val s = state().copy(enrolledOn = "2026-03-27")
        assertEquals("absence", Rules.reminderKind(s, Instant.parse("2026-03-31T05:30:00Z"), ZoneId.of("Europe/Madrid")))
        assertNull(Rules.reminderKind(s, Instant.parse("2026-03-31T05:30:00Z"), ZoneId.of("America/New_York")))
    }
    @Test fun pausesSnoozeAndActiveSessionsSuppressAbsence() {
        val now = Instant.parse("2026-01-10T12:00:00Z")
        val zone = ZoneId.of("UTC")
        assertNull(Rules.reminderKind(state().copy(settings = Settings(pauseFrom = "2026-01-09", pauseUntil = "2026-01-10")), now, zone))
        assertNull(Rules.reminderKind(state().copy(settings = Settings(pauseFrom = "2026-01-06", pauseUntil = "2026-01-09")), now, zone))
        assertNull(Rules.reminderKind(state().copy(settings = Settings(snoozeUntil = now.toEpochMilli() + 1000)), now, zone))
        assertNull(Rules.reminderKind(state().copy(activeSession = completed().copy(status = SessionStatus.WEIGHTS_ACTIVE)), now, zone))
        assertEquals("steps", Rules.reminderKind(state().copy(activeSession = completed().copy(status = SessionStatus.CARDIO_PENDING)), now, zone))
    }
    @Test fun backupRoundTripAndRepeatedRestoreDoNotDuplicate() {
        val s = state().copy(workouts = listOf(completed()), weights = listOf(WeightLog(date = date.toString(), kg = 80.0)))
        val restored = BackupCodec.decode(BackupCodec.encode(s), catalog)
        assertEquals(s, restored)
        assertEquals(s, BackupCodec.decode(BackupCodec.encode(restored), catalog))
    }
    @Test fun backupRejectsInvalidVersionReferencesSnapshotsAndDuplicates() {
        invalid { BackupCodec.decode(BackupCodec.encode(state()).replace("\"version\":1", "\"version\":9"), catalog) }
        invalid { BackupCodec.validate(state().copy(customRecipes = listOf(RecipeVersion("bad", name = "bad", ingredients = listOf(Ingredient("missing", 10.0))))), catalog) }
        val m = MealLog(date = date.toString(), slot = "snack", name = "bad", ingredients = listOf(IngredientSnapshot(food, 100.0)), nutrition = Nutrition())
        invalid { BackupCodec.validate(state().copy(mealLogs = listOf(m)), catalog) }
        val w = WeightLog(date = date.toString(), kg = 80.0)
        invalid { BackupCodec.validate(state().copy(weights = listOf(w, w)), catalog) }
        invalid { BackupCodec.validate(state().copy(workouts = listOf(completed().copy(steps = StepState()))), catalog) }
    }
    @Test fun backupRejectsOversizeBeforeParse() { invalid { BackupCodec.decode(" ".repeat(BackupCodec.MAX_BYTES + 1), catalog) } }
    @Test fun backupCannotForgeCompletionWithInvalidCardioOrNoStepSource() {
        val original = completed()
        invalid { BackupCodec.validate(state().copy(workouts = listOf(original.copy(timedCardioMinutes = -1, cardioStartedAt = friday))), catalog) }
        invalid { BackupCodec.validate(state().copy(workouts = listOf(original.copy(steps = original.steps.copy(source = "none")))), catalog) }
        invalid { BackupCodec.validate(state().copy(workouts = listOf(original.copy(steps = original.steps.copy(lastReadAt = original.startedAt - 1)))), catalog) }
        invalid { BackupCodec.validate(state().copy(workouts = listOf(original.copy(plan = emptyList()))), catalog) }
        invalid { BackupCodec.validate(state().copy(workouts = listOf(original.copy(plan = listOf(plan, plan)))), catalog) }
        invalid { BackupCodec.validate(state().copy(customFoods = listOf(food)), catalog) }
        invalid { BackupCodec.validate(state().copy(mealOverrides = listOf(MealOverride(date.toString(), "breakfast", "recipe", grams = mapOf("missing" to 100.0)))), catalog) }
    }
    @Test fun unknownWeightsAreNotZerosAndMissingDaysDoNotCountInAverage() {
        assertNull(Rules.weightAverage(emptyList(), date))
        val measurements = listOf(WeightLog(date = date.toString(), kg = 80.0), WeightLog(date = date.minusDays(6).toString(), kg = 82.0), WeightLog(date = date.minusDays(8).toString(), kg = 99.0))
        assertEquals(81.0, Rules.weightAverage(measurements, date)!!, 0.0)
    }
}
