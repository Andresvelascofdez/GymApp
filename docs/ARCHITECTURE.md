# Arquitectura propuesta

Android nativo, Kotlin, Jetpack Compose y Material 3. Room para datos relacionales y DataStore
para preferencias. Coroutines/Flow, estado unidireccional y ViewModels. Contenedor DI sencillo;
introducir una librería solo si aporta valor. No crear backend ni cuenta ni dependencia de IA.

Elegir versiones actuales estables COMPATIBLES y fijarlas en catálogo Gradle. MinSdk orientativo
28, con disponibilidad Health Connect comprobada en runtime; compilar/probar específicamente
el comportamiento de Android 16 del dispositivo objetivo. No imponer SDK más nuevo que el
soportado por el toolchain sin comprobar compatibilidad. Incluir Wrapper verificable y lock/checksum.

Estructura orientativa, adaptable con justificación:
- app: ensamblado, navegación, plataforma y UI.
- core/domain: modelos puros, cálculo, progresión, reloj abstracto y reglas de sesión.
- core/data: Room, repositorios, JSON versionado, snapshots, exportación y restauración.
- feature/training, feature/nutrition, feature/progress, feature/settings.
- platform/health, platform/focus, platform/notifications.
No convertir cada carpeta en módulo Gradle innecesario: un solo módulo app con paquetes puede bastar.

Modelos: Profile, Goals, FoodVersion, Ingredient, RecipeVersion, WeekPlan, PlannedMeal,
MealLog(snapshot), Exercise, RoutineVersion, PlannedExercise, WorkoutSession, SetLog,
StepState, FocusSessionState, ReminderState, WeightLog, WellbeingLog y BackupEnvelope.
UUID estables, medidas métricas, Instant para eventos y ZoneId/LocalDate para calendario.
Revisar unidades de cargas: por mancuerna vs total; mostrarlo explícitamente y guardarlo.

SessionState: idle -> weights_active -> cardio_pending -> completed, con suspended,
abandoned, emergency_exit y sensor_unverified como resultados explícitos, no pasos ficticios.
Persistir transiciones y registros de forma atómica. Pantalla bloqueada no equivale a sesión cerrada.

Puertos testeables: TimeProvider, StepsProvider, FocusController, ReminderScheduler,
NutritionCalculator, WorkoutRepository, BackupRepository. Fakes solo en tests/demo explícito;
producción usa las integraciones nativas. No dejar implementaciones vacías «para después».

Nutrición por Decimal o enteros con escala definida y redondeo solo al mostrar. Energía de cada
alimento es la del origen, no recalculada forzosamente por macros. La app conserva referencias
a la versión y también snapshot de lo consumido para evitar cambios retroactivos.

Perfil: lectura opcional de un asset de build PERSONAL generado desde .private/profile.local.json
en una carpeta build/ ignorada; no copiarlo a src/main/assets. Una propiedad Gradle explícita
activa el build personal; por defecto build genérico sin datos personales. Implementar también
importación local mediante SAF/onboarding; no incluir datos reales en previews o fixtures.

La interfaz debe arrancar sin permisos especiales. Funciones no autorizadas muestran su estado,
mientras entrenamiento y dieta siguen funcionando. Permisos se solicitan con contexto funcional.
