"""Validate the complete public reference package using decimal ingredient arithmetic."""
import json
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(name):
    return json.loads((ROOT / name).read_text(encoding="utf-8"), parse_float=Decimal)

def validate():
    foods = {f["id"]: f for f in read("data/nutrition/foods.json")["foods"]}
    recipes = {r["id"]: r for r in read("data/nutrition/recipes.json")["recipes"]}
    week = read("data/nutrition/week_plan.json")
    metrics = ["energy_kcal", "protein_g", "fat_g", "carbohydrates_available_g", "fiber_g"]
    assert len(recipes) == 20
    totals = {}
    for recipe in recipes.values():
        assert recipe["version"] >= 1 and recipe["instructions_es"] and recipe["storage_note_es"]
        total = {key: Decimal(0) for key in metrics}
        for ingredient in recipe["ingredients"]:
            assert ingredient["grams"] > 0
            food = foods[ingredient["food_id"]]
            assert food["source_id"] and food["reference_g"] == 100
            for key in metrics:
                assert food[key] >= 0
                total[key] += Decimal(str(food[key])) * Decimal(str(ingredient["grams"])) / 100
        for key in metrics:
            assert abs(total[key] - recipe["nutrition_reference"][key]) <= Decimal("0.015"), (recipe["id"], key)
        totals[recipe["id"]] = total
    assert len(week["days"]) == 5
    for day in week["days"]:
        assert set(day["meals"]) == {"breakfast", "lunch", "dinner", "snack"}
        energy = sum(totals[r]["energy_kcal"] for r in day["meals"].values())
        protein = sum(totals[r]["protein_g"] for r in day["meals"].values())
        assert 2000 <= energy <= 2200 and 190 <= protein <= 215
        print(f"{day['weekday']}: {energy:.2f} kcal; {protein:.2f} g protein")
    routine = read("data/training/routine.json")
    exercises = {e["id"]: e for e in routine["exercises"]}
    assert len(routine["sessions"]) == 5
    for exercise in exercises.values():
        for alternative in exercise["alternatives"]:
            assert alternative in exercises
    for session in routine["sessions"]:
        assert len(session["exercises"]) >= 5
        for exercise in session["exercises"]:
            assert exercise["exercise_id"] in exercises
            assert exercise["sets"] > 0 and 0 < exercise["reps_min"] <= exercise["reps_max"]
            assert exercise["rest_seconds"] > 0
    assert not {"lunges", "abdominal_crunch"}.intersection(exercises)
    expected = {i["food_id"]: Decimal(str(i["grams"])) for i in read("data/nutrition/shopping_reference.json")["items"]}
    actual = {}
    for recipe in recipes.values():
        for i in recipe["ingredients"]:
            actual[i["food_id"]] = actual.get(i["food_id"], Decimal(0)) + Decimal(str(i["grams"]))
    assert actual == expected
    example = read("data/profile.example.json")
    assert all(example[k] is None for k in ("age_years", "height_cm", "current_weight_kg", "target_weight_kg"))
    print(f"PASS: {len(foods)} foods, 20 recipes, 5 menus, 5 routines, shopping and anonymous profile")

if __name__ == "__main__":
    validate()
