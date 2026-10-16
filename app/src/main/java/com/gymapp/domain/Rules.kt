package com.gymapp.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor

object Rules {
    fun nutrition(ingredients: List<IngredientSnapshot>): Nutrition {
        fun sum(value: (FoodVersion) -> Double): Double = ingredients.fold(BigDecimal.ZERO) { total, i ->
            require(i.grams.isFinite() && i.grams >= 0 && i.food.referenceGrams > 0)
            total + BigDecimal.valueOf(value(i.food)).multiply(BigDecimal.valueOf(i.grams)).divide(BigDecimal.valueOf(i.food.referenceGrams), 16, java.math.RoundingMode.HALF_EVEN)
        }.toDouble()
        return Nutrition(sum { it.kcal }, sum { it.protein }, sum { it.fat }, sum { it.carbs }, sum { it.fiber })
    }

    fun sensorReading(steps: StepState, raw: Long, bootId: String, at: Long): StepState {
        require(raw >= 0)
        if (steps.lastReadAt != null && at < steps.lastReadAt) return steps
        if (steps.baseline == null) return steps.copy(baseline = raw, lastRaw = raw, bootId = bootId, lastReadAt = at, status = "Sensor activo; base desde primera lectura")
        if (steps.bootId != bootId || raw < (steps.lastRaw ?: raw)) return steps.copy(baseline = raw, lastRaw = raw, bootId = bootId, lastReadAt = at, status = "Sensor reiniciado; intervalo sin verificar")
        return steps.copy(count = steps.count + raw - (steps.lastRaw ?: raw), lastRaw = raw, lastReadAt = at, status = if (steps.status.contains("sin verificar")) steps.status else "Sensor activo")
    }

    fun progression(plan: PlannedExercise, logs: List<SetLog>, increment: Double): Double? {
        require(increment.isFinite() && increment > 0)
        val effective = logs.filter { it.exerciseId == plan.exerciseId && !it.warmup }.sortedBy { it.index }
        if (effective.size != plan.sets || effective.map { it.index }.distinct().size != plan.sets) return null
        if (effective.any { it.reps < plan.repsMax || !it.techniqueGood || it.rir == null || it.rir < plan.targetRir }) return null
        val weight = effective.maxOf { it.weight }
        if (effective.any { it.weight != weight }) return null
        return (floor(weight / increment + 1e-9) + 1) * increment
    }

    fun lastTrainingDate(state: AppState, zone: ZoneId): LocalDate? = state.workouts.plus(listOfNotNull(state.activeSession))
        .filter { it.weightsCompletedAt != null && it.workSets.isNotEmpty() }
        .maxOfOrNull { Instant.ofEpochMilli(it.weightsCompletedAt!!).atZone(zone).toLocalDate() }

    fun reminderKind(state: AppState, now: Instant, zone: ZoneId): String? {
        val settings = state.settings
        if (!settings.reminderEnabled || settings.snoozeUntil?.let { now.toEpochMilli() < it } == true) return null
        val local = now.atZone(zone)
        val date = local.toLocalDate()
        val start = LocalTime.parse(settings.reminderStart)
        val end = LocalTime.parse(settings.reminderEnd)
        val time = local.toLocalTime()
        val inWindow = if (start <= end) time >= start && time < end else time >= start || time < end
        if (!inWindow) return null
        val pauseStart = settings.pauseFrom?.let(LocalDate::parse)
        val pauseEnd = settings.pauseUntil?.let(LocalDate::parse)
        if (pauseStart != null && pauseEnd != null && date >= pauseStart && date <= pauseEnd) return null
        if (settings.lastReminderAt?.let { now.toEpochMilli() - it < settings.reminderIntervalMinutes * 60_000L } == true) return null
        state.activeSession?.takeIf { it.active }?.let { return if (it.status == SessionStatus.CARDIO_PENDING) "steps" else null }
        val base = listOfNotNull(lastTrainingDate(state, zone), LocalDate.parse(state.enrolledOn), pauseEnd?.takeIf { it < date }).max()
        return if (date >= base.plusDays(4)) "absence" else null
    }

    fun weightAverage(weights: List<WeightLog>, date: LocalDate): Double? = weights.filter { LocalDate.parse(it.date) in date.minusDays(6)..date }.takeIf { it.isNotEmpty() }?.map { it.kg }?.average()
    fun weeklyWeightChange(weights: List<WeightLog>, date: LocalDate): Double? {
        val current = weightAverage(weights, date) ?: return null
        val previous = weightAverage(weights, date.minusDays(7)) ?: return null
        return current - previous
    }
}
