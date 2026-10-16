package com.gymapp.domain

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gymapp.BuildConfig
import com.gymapp.data.BackupCodec
import com.gymapp.data.Store
import androidx.room.RoomDatabase
import org.junit.Before
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StoreTest {
    @Before fun isolateApplicationDatabase() {
        val field = Store::class.java.getDeclaredField("instance").apply { isAccessible = true }
        (field.get(null) as? Store)?.let { previous ->
            val database = Store::class.java.getDeclaredField("database").apply { isAccessible = true }
            (database.get(previous) as RoomDatabase).close()
        }
        field.set(null, null)
    }
    private fun store(): Store {
        assumeFalse("Solo datos sintéticos en el build genérico", BuildConfig.APPLICATION_ID.endsWith(".personal"))
        return Store.get(ApplicationProvider.getApplicationContext())
    }
    private fun invalid(action: () -> Unit) { try { action(); fail("Debía rechazar la acción") } catch (_: IllegalArgumentException) { } }

    @Test fun mealsAreIdempotentVersionedAndRestorationIsAtomic() {
        val store = store()
        store.importJson(BackupCodec.encode(AppState(enrolledOn = "2026-01-02")))
        val day = LocalDate.of(2026, 1, 5)
        assertEquals(4, store.planFor(day).size)
        assertTrue(store.planFor(day.plusDays(5)).isEmpty())
        assertTrue(store.state.value.mealLogs.isEmpty())
        store.setMealConsumed(day, "breakfast", true)
        store.setMealConsumed(day, "breakfast", true)
        assertEquals(1, store.state.value.mealLogs.size)
        val snapshot = store.state.value.mealLogs.single()
        val ingredient = snapshot.ingredients.first()
        store.saveFood(ingredient.food.copy(kcal = ingredient.food.kcal + 100))
        assertEquals(snapshot, store.state.value.mealLogs.single())
        assertNotEquals(snapshot.nutrition.kcal, store.mealSnapshot(day, "breakfast").nutrition.kcal)
        store.setMealOverride(MealOverride(day.toString(), "breakfast", snapshot.recipeId!!, portions = 2.0))
        assertEquals(snapshot, store.state.value.mealLogs.single())
        store.refreshConsumedMeal(day, "breakfast")
        assertEquals(1, store.state.value.mealLogs.size)
        store.buildShopping(listOf(day), 2.0)
        assertTrue(store.state.value.shopping.isNotEmpty())
        assertEquals(store.state.value.shopping.map { it.foodId }.distinct().size, store.state.value.shopping.size)
        val exported = store.exportJson()
        val before = store.state.value
        invalid { store.importJson(exported.replace("\"version\":1", "\"version\":999")) }
        assertEquals(before, store.state.value)
        store.importJson(exported); store.importJson(exported)
        assertEquals(before, store.state.value)
        store.setMealConsumed(day, "breakfast", false); store.setMealConsumed(day, "breakfast", false)
        assertTrue(store.state.value.mealLogs.isEmpty())
    }

    @Test fun sessionRecoversDurablyWithoutFalseSetsAndTenConfirmationExit() {
        val store = store()
        store.importJson(BackupCodec.encode(AppState(enrolledOn = "2026-01-02")))
        store.startSession(abbreviated = true)
        val session = store.state.value.activeSession!!
        assertEquals("none", session.steps.source)
        assertEquals(3, session.plan.size); assertTrue(session.plan.all { it.sets == 2 })
        assertTrue(session.sets.isEmpty())
        invalid { store.finishWeights() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val constructor = Store::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val recovered = constructor.newInstance(context)
        assertEquals(session.id, recovered.state.value.activeSession!!.id)
        assertEquals(session.startedAt, recovered.state.value.activeSession!!.startedAt)
        assertTrue(recovered.state.value.activeSession!!.sets.isEmpty())
        val exercise = session.plan.first()
        val set = SetLog(exerciseId = exercise.exerciseId, index = 0, weight = 10.0, reps = 10)
        store.saveSet(set); store.saveSet(set)
        assertEquals(1, store.state.value.activeSession!!.sets.size)
        invalid { store.finishWeights() }
        store.finishWeights("Sesión abreviada con menos series por cansancio")
        assertEquals(SessionStatus.CARDIO_PENDING, store.state.value.activeSession!!.status)
        assertEquals(1, store.state.value.nextRoutineIndex)
        invalid { store.closeSession(SessionStatus.COMPLETED) }
        store.updateSessionSteps(session.id, 9999, System.currentTimeMillis(), "Lectura ajena", "sensor")
        assertEquals(0L, store.state.value.activeSession!!.steps.count)
        store.closeSession(SessionStatus.SENSOR_UNVERIFIED, "No hay sensor autorizado")
        assertTrue(store.state.value.workouts.single().abbreviated)
        store.startSession()
        val next = store.state.value.activeSession!!.routineId
        assertNotEquals(session.routineId, next)
        repeat(4) { assertFalse(store.confirmAbandon()) }
        store.cancelAbandon(); assertEquals(0, store.state.value.activeSession!!.abandonmentConfirmations)
        repeat(9) { assertFalse(store.confirmAbandon()) }
        assertTrue(store.confirmAbandon()); assertNull(store.state.value.activeSession)
        assertEquals(1, store.state.value.nextRoutineIndex)
        store.startSession(); assertEquals(next, store.state.value.activeSession!!.routineId)
        store.closeSession(SessionStatus.EMERGENCY_EXIT, "Salida de emergencia sintética")
        assertNull(store.state.value.activeSession)
    }

    @Test fun ingredientReplacementIsAtomicAndOtherDatesKeepTheirRecipeVersion() {
        val store = store()
        store.importJson(BackupCodec.encode(AppState()))
        val first = LocalDate.of(2026, 1, 5)
        val second = first.plusDays(7)
        val recipe = store.planFor(first).first { it.first == "breakfast" }.second
        val originalGrams = recipe.ingredients.associate { it.foodId to it.grams }
        store.setMealOverride(MealOverride(first.toString(), "breakfast", recipe.id, grams = originalGrams))
        store.setMealOverride(MealOverride(second.toString(), "breakfast", recipe.id, grams = originalGrams))
        store.setMealConsumed(first, "breakfast", true)
        val consumed = store.state.value.mealLogs.single()
        val replacement = store.catalog.foods.first { f -> recipe.ingredients.none { it.foodId == f.id } }
        val edited = recipe.copy(ingredients = recipe.ingredients.mapIndexed { index, i -> if (index == 0) i.copy(foodId = replacement.id, foodVersion = replacement.version) else i })
        store.saveMealEdit(edited, MealOverride(first.toString(), "breakfast", recipe.id, grams = edited.ingredients.associate { it.foodId to it.grams }), false)
        assertEquals(edited.ingredients, store.planFor(first).first { it.first == "breakfast" }.second.ingredients)
        assertEquals(recipe, store.planFor(second).first { it.first == "breakfast" }.second)
        assertEquals(consumed, store.state.value.mealLogs.single())
        store.saveMealEdit(edited, MealOverride(first.toString(), "breakfast", recipe.id, grams = edited.ingredients.associate { it.foodId to it.grams }), true)
        assertEquals(consumed.id, store.state.value.mealLogs.single().id)
        assertEquals(replacement.id, store.state.value.mealLogs.single().ingredients.first().food.id)
        assertEquals(store.state.value, BackupCodec.decode(store.exportJson(), store.catalog))
    }

    @Test fun lateAggregatesReplaceTotalsWhileSessionSourceRemainsFixed() {
        val store = store()
        store.importJson(BackupCodec.encode(AppState(settings = Settings(stepSource = "health_connect"))))
        store.startSession()
        val session = store.state.value.activeSession!!
        store.updateSessionSteps(session.id, 9000, session.startedAt - 1, "Before session", "health_connect")
        assertEquals(0L, store.state.value.activeSession!!.steps.count)
        store.updateSessionSteps(session.id, 100, session.startedAt + 1000, "Provider aggregate", "health_connect")
        store.updateSessionSteps(session.id, 160, session.startedAt + 2000, "Late provider aggregate", "health_connect")
        assertEquals(160L, store.state.value.activeSession!!.steps.count)
        store.updateSessionSteps(session.id, 140, session.startedAt + 3000, "Provider correction", "health_connect")
        assertEquals(140L, store.state.value.activeSession!!.steps.count)
        store.update { it.copy(settings = it.settings.copy(stepSource = "sensor")) }
        store.updateSessionSteps(session.id, 9999, session.startedAt + 4000, "Other source", "sensor")
        store.sensorReading(session.id, 10000, "synthetic_boot", session.startedAt + 5000)
        assertEquals("health_connect", store.state.value.activeSession!!.steps.source)
        assertEquals(140L, store.state.value.activeSession!!.steps.count)
    }
}
