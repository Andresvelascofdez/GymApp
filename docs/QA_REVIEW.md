# Independent implementation review

Reviewed the integrated source against PRODUCT_SPEC.md and AC01–AC32. This review used public specifications, source, synthetic tests and generated test reports. It did not read or reproduce the private profile. Build, emulator and publication results belong in DELIVERY_REPORT.md; source inspection alone does not establish a hardware PASS.

## Defects identified and corrected

| Priority | Finding and trigger | Correction and source evidence |
| --- | --- | --- |
| Must fix | Selector import rejected valid exported backups between 5 MB and the store's 8 MiB limit. | The selector and codec now share `BackupCodec.MAX_BYTES`; the selector reads at most one byte beyond the limit before rejecting. `app/src/main/java/com/gymapp/ui/GymApp.kt:108`, `app/src/main/java/com/gymapp/data/BackupCodec.kt:11`. |
| Must fix | Replacing an ingredient after saving meal quantities could validate a new recipe against the old override before saving the replacement override. | `Store.saveMealEdit` saves the version, date override and optional consumed snapshot in one validated database update. Other dates retain their recipe version and an explicitly updated consumption retains its identifier. `app/src/main/java/com/gymapp/data/Store.kt:204`, `app/src/main/java/com/gymapp/data/Store.kt:212`. Regression: `ingredientReplacementIsAtomicAndOtherDatesKeepTheirRecipeVersion` in StoreTest. |
| Must fix | Recipe previews could silently show zero nutrition for a pinned older food version after its label was edited. | Preview calculations receive all catalog/custom food versions and resolve the pinned version. They require an existing food rather than substituting a zero value. `app/src/main/java/com/gymapp/ui/GymApp.kt:62`, `app/src/main/java/com/gymapp/ui/GymApp.kt:500`, `app/src/main/java/com/gymapp/ui/GymApp.kt:546`. |
| Must fix | Imported routine overrides with duplicate exercises were accepted but could not start because session validation rejected the resulting plan. | Both routine and session plans validate unique exercise identifiers and a 0–10 target RIR. `app/src/main/java/com/gymapp/data/BackupCodec.kt:53`, `app/src/main/java/com/gymapp/data/BackupCodec.kt:69`. |
| Must fix | A date-specific recipe version could be replaced by the globally newest version when its editor opened. | The editor retains its initial recipe version until the user chooses another recipe. `app/src/main/java/com/gymapp/ui/GymApp.kt:539`. |
| Must fix | Replacing an ingredient with an already included food could collapse gram-map keys. | The substitution list disables foods already present and explains that state. `app/src/main/java/com/gymapp/ui/GymApp.kt:558`. |
| Must fix | Public catalog entries explicitly containing a null source record failed parsing and prevented store initialization. | `FoodVersion.sourceRecord` is nullable, preserving the catalog's unknown metadata. `app/src/main/java/com/gymapp/domain/Models.kt:35`. The domain catalog test subsequently passed in the generated test report. |
| Presentation | Several newly written Spanish documents and the Health Connect rationale contained literal question marks in place of accents. | Restored UTF-8 text in README.md, docs/UI_DESIGN.md and HealthRationaleActivity.kt; searched the edited files for remaining corrupted words. |

## Checked invariants

- Session source, goal and focus request are captured at start. Step updates require matching session and source; earlier measurements are rejected. Phone counter baselines and deltas live in the persisted session. Reset/reboot gaps remain explicit and do not silently qualify as verified completion.
- Health Connect requests one aggregate from the session's persisted Instant. The implementation does not add individual overlapping records or phone sensor totals to that aggregate. Origins are shown only from returned provider metadata.
- Weight completion requires actual non-warmup sets. Finishing weights advances the pending routine once; abandoning an uncompleted routine does not advance it. Ten abandonment confirmations can be cancelled and emergency exits bypass the chain.
- The reminder rules use local calendar dates and current ZoneId; Friday-to-Tuesday, missing history, DST/time zones, pause, snooze and active-session cases have synthetic domain tests. Notification delivery remains subject to Android scheduling and channel settings.
- Backup decoding validates before database replacement. Store updates validate and serialize the complete aggregate before a Room transaction, then publish state. Planned meals and consumed snapshots remain separate entities.
- The manifest requests no Internet access and disables Android automatic backup. No analytics, runtime model API, advertising or mandatory account code was identified. Generic/personal build separation and forbidden payload checks are explicit; final staging must still be scanned by the publication owner.

## Evidence limits

No unresolved must-fix source defect remained among the findings above after reinspection. This is a scoped review, not a claim of exhaustive verification. Final test and lint reruns must include the corrected implementation; transient earlier failing reports must not be described as final PASS results.

Actual Health Connect overlapping/late provider records, exact sensor boundary behavior on a physical phone, service survival under POCO/HyperOS and actual watch-to-Mi Fitness sharing require device evidence. AC14 provider integration and AC31 physical outcomes must remain NOT_RUN wherever that evidence is absent. An emulator barrier test can establish the tested Android behavior, but cannot establish vendor firmware survival.
