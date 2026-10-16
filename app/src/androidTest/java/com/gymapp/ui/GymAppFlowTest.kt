package com.gymapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.gymapp.MainActivity
import com.gymapp.data.Store
import com.gymapp.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GymAppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var store: Store

    @Before fun resetSyntheticState() {
        compose.runOnIdle {
            store = Store.get(compose.activity)
            store.update { AppState(settings = Settings(focusEnabled = false, reminderEnabled = false)) }
        }
        if(compose.onAllNodesWithText("Tu constancia, a tu ritmo").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Empezar", useUnmergedTree = true).performClick()
        }
        compose.waitForIdle()
    }

    @Test fun allDestinationsWorkWithoutSpecialPermissions() {
        compose.onNodeWithTag("nav_1").performClick()
        compose.onNodeWithText("Hoy me cuesta · plan abreviado").assertExists()
        compose.onNodeWithTag("nav_2").performClick()
        compose.onNodeWithText("+ Registrar alimento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav_3").performClick()
        compose.onNodeWithText("+ Registrar peso").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav_4").performClick()
        compose.onNodeWithText("Exportar copia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("nav_0").performClick()
        compose.onNodeWithText("Hoy cuenta.").assertExists()
    }

    @Test fun recordRealSetAndEmergencyPreservesHistory() {
        compose.onNodeWithText("Empezar entrenamiento").performScrollTo().performClick()
        compose.onAllNodesWithText("+ Registrar serie").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Carga (kg)").performTextReplacement("12.5")
        compose.onNodeWithText("Repeticiones").performTextReplacement("10")
        compose.onNodeWithText("Guardar serie").performClick()
        compose.waitForIdle()
        val id = store.state.value.activeSession!!.id
        assertEquals(1, store.state.value.activeSession!!.workSets.size)
        assertEquals(12.5, store.state.value.activeSession!!.workSets.single().weight, 0.001)
        compose.onNodeWithText("Salida de emergencia").performScrollTo().performClick()
        compose.waitForIdle()
        assertNull(store.state.value.activeSession)
        val saved = store.state.value.workouts.single()
        assertEquals(id, saved.id)
        assertEquals(SessionStatus.EMERGENCY_EXIT, saved.status)
        assertEquals(1, saved.workSets.size)
    }

    @Test fun abandonmentCanCancelAndEmergencyExitsInsideChain() {
        compose.onNodeWithText("Empezar entrenamiento").performScrollTo().performClick()
        compose.onNodeWithText("Abandonar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar 1/10").performClick()
        compose.onNodeWithText("Confirmar 2/10").performClick()
        compose.onNodeWithText("Volver al entrenamiento").performClick()
        compose.waitForIdle()
        assertEquals(0, store.state.value.activeSession!!.abandonmentConfirmations)
        compose.onNodeWithText("Abandonar sesión").performScrollTo().performClick()
        compose.onNode(hasText("Salida de emergencia") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertNull(store.state.value.activeSession)
        assertTrue(store.state.value.workouts.single().workSets.isEmpty())
        assertNull(store.state.value.workouts.single().weightsCompletedAt)
    }

    @Test fun consumptionIsIdempotentAndChangesKeepHistoricalSnapshot() {
        val monday = LocalDate.of(2026, 10, 12)
        compose.runOnIdle {
            store.setMealConsumed(monday, "breakfast", true)
            store.setMealConsumed(monday, "breakfast", true)
            val old = store.state.value.mealLogs.single()
            val food = old.ingredients.first().food
            store.saveFood(food.copy(kcal = food.kcal + 10.0))
            assertEquals(old, store.state.value.mealLogs.single())
            store.setMealConsumed(monday, "breakfast", false)
            store.setMealConsumed(monday, "breakfast", false)
            assertTrue(store.state.value.mealLogs.isEmpty())
            store.setMealConsumed(monday, "breakfast", true)
            val exported = store.exportJson()
            val log = store.state.value.mealLogs.single()
            store.importJson(exported)
            store.importJson(exported)
            assertEquals(listOf(log), store.state.value.mealLogs)
        }
    }

    @Test fun weightFormStoresMeasurementAndShowsAverage() {
        compose.onNodeWithTag("nav_3").performClick()
        compose.onNodeWithText("+ Registrar peso").performScrollTo().performClick()
        compose.onNodeWithText("Peso (kg)").performTextReplacement("80.5")
        compose.onNodeWithText("Guardar peso").performClick()
        compose.waitForIdle()
        assertEquals(80.5, store.state.value.weights.single().kg, 0.001)
        compose.onAllNodesWithText("80,5 kg").onFirst().assertExists()
    }

    @Test fun activityRecreationKeepsSessionIdentityAndRecordedSet() {
        compose.onNodeWithText("Empezar entrenamiento").performScrollTo().performClick()
        compose.onAllNodesWithText("+ Registrar serie").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Carga (kg)").performTextReplacement("15")
        compose.onNodeWithText("Repeticiones").performTextReplacement("8")
        compose.onNodeWithText("Guardar serie").performClick()
        compose.waitForIdle()
        val before = store.state.value.activeSession!!
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_1").performClick()
        compose.onNodeWithText("Serie 1 · 15,0 kg × 8  ✎").performScrollTo().assertIsDisplayed()
        val after = store.state.value.activeSession!!
        assertEquals(before.id, after.id)
        assertEquals(before.startedAt, after.startedAt)
        assertEquals(before.sets, after.sets)
    }

    @Test fun normalAbandonmentRequiresAllTenConfirmations() {
        compose.onNodeWithText("Empezar entrenamiento").performScrollTo().performClick()
        compose.onNodeWithText("Abandonar sesión").performScrollTo().performClick()
        for(step in 1..9) {
            compose.onNodeWithText("Confirmar $step/10").performClick()
            compose.waitForIdle()
            assertNotNull(store.state.value.activeSession)
        }
        compose.onNodeWithText("Confirmar abandono").performClick()
        compose.waitForIdle()
        assertNull(store.state.value.activeSession)
        assertEquals(SessionStatus.ABANDONED, store.state.value.workouts.single().status)
    }

    @Test fun mealQuantityFormRecalculatesPlanAndPreservesConsumedSnapshot() {
        val today = LocalDate.now()
        compose.runOnIdle { store.setMealOverride(MealOverride(date = today.toString(), slot = "breakfast", recipeId = "monday_breakfast_v1")) }
        compose.onNodeWithTag("nav_2").performClick()
        compose.onAllNodesWithText("Marcar como consumida").onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        val old = store.state.value.mealLogs.single()
        val ingredient = old.ingredients.first()
        compose.onAllNodesWithText("Ver / editar / sustituir").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("${ingredient.food.name} (g)").performScrollTo().performTextReplacement((ingredient.grams + 20).toString())
        compose.onNodeWithText("Guardar cantidades").performClick()
        compose.waitForIdle()
        assertEquals(old, store.state.value.mealLogs.single())
        assertTrue(store.mealSnapshot(today, "breakfast").nutrition.kcal > old.nutrition.kcal)
        compose.onNodeWithText("Registrada · desmarcar para deshacer").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(store.state.value.mealLogs.isEmpty())
    }

    @Test fun freeFoodQuantityEditorRetainsOriginalLabelAndIdentity() {
        compose.runOnIdle { store.logFood(LocalDate.now(), "snack", "banana", 100.0) }
        val old = store.state.value.mealLogs.single()
        compose.onNodeWithTag("nav_2").performClick()
        compose.onNodeWithText("Editar gramos").performScrollTo().performClick()
        compose.onNodeWithText("${old.ingredients.single().food.name} (g)").performTextReplacement("150")
        compose.onNodeWithText("Guardar consumo").performClick()
        compose.waitForIdle()
        val revised = store.state.value.mealLogs.single()
        assertEquals(old.id, revised.id)
        assertEquals(old.ingredients.single().food, revised.ingredients.single().food)
        assertEquals(old.nutrition.kcal * 1.5, revised.nutrition.kcal, 0.001)
    }
}
