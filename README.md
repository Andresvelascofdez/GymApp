# GymApp

Aplicación Android nativa en español para organizar fuerza, comidas y constancia. Interfaz oscura, datos locales y funcionamiento sin cuenta ni servidor.

Incluye cinco sesiones consecutivas editables, series y calentamientos, descansos, alternativas, progresión aceptada por el usuario y modo abreviado. Los pasos se miden desde iniciar cada sesión mediante una fuente fija: Health Connect o sensor del móvil. La concentración voluntaria permite GymApp y Spotify, con acceso al sistema y salida de emergencia.

Las veinte recetas forman cinco menús completos de lunes a viernes. Se calculan por ingredientes, con registro separado del plan, cantidades editables, alimentos de etiqueta, sustituciones, cocina por tandas, favoritos y compra agregada. Los consumos conservan sus snapshots. Peso, medias semanales, bienestar y copias versionadas completan las cinco pantallas.

## Compilar y verificar

Requisitos: JDK 21, Android SDK Platform 36 y Build Tools 36.0.0. El Wrapper descarga Gradle 9.3.1 y verifica su SHA256. AGP 9.1.1, Kotlin 2.2.10 integrado, KSP 2.3.12 y Compose BOM 2026.03.01 están fijados. Se usa una BOM estable compatible con SDK 36; versiones posteriores requieren otro SDK. Dependencias verificadas mediante `gradle/verification-metadata.xml`.

```sh
python scripts/validate_brief.py
./gradlew testDebugUnitTest lintDebug assembleDebug
# Con un emulador Android 16 disponible:
./gradlew connectedDebugAndroidTest
```

En Windows usa `gradlew.bat`. Configura `ANDROID_HOME` o un `local.properties` ignorado con `sdk.dir`. No se necesita Android Studio. Android mínimo 9/API 28; Health Connect se comprueba en ejecución. El workflow construye el APK genérico sin archivos privados y publica APK e informes como artifact `gymapp-generic-debug`.

## Instalar

Descarga el APK genérico desde el artifact del workflow aprobado del commit que quieras instalar. Extrae el ZIP del artifact y abre el APK en Android, autorizando la instalación desde esa fuente. Alternativamente:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Es un APK de prueba firmado con la clave debug de la herramienta. No es una firma release propia. Builds de CI y locales pueden usar distintas claves debug: Android puede rechazar una actualización entre ellas; exporta antes tus datos si necesitas reinstalar. El APK personal tiene otro identificador para poder coexistir.

El build genérico comienza sin perfil personal. Puedes importar un perfil JSON desde Ajustes o registrar tus datos en la app. El perfil anónimo de formato está en `data/profile.example.json`. Para generar un APK personal exclusivamente local, crea `.private/profile.local.json` y ejecuta:

```sh
./gradlew assembleDebug -Ppersonal=true
```

El perfil se genera como asset dentro de `app/build/`, nunca en los assets públicos. Una compilación genérica posterior elimina ese asset. No publiques el APK personal, bases, exportaciones, claves ni el paquete original.

`python scripts/build_apks.py --personal` produce los dos APK con nombres distintos en `local-artifacts/`, comprueba los assets y escribe sus SHA256; termina restaurando la salida de compilación genérica. Omite `--personal` para generar solo el genérico.

## Permisos e integraciones

La app arranca sin permisos especiales. En Ajustes elige la fuente para próximas sesiones. Health Connect solo solicita lectura de pasos y consulta mientras la app está visible; habilita el intercambio de Mi Fitness si tu versión/reloj lo soporta. La ausencia de registros es «Sin datos». Los datos pueden llegar tarde y el agregado respeta prioridades del proveedor; no se suman reloj y móvil. No hay API directa de Xiaomi.

El sensor requiere permiso de actividad y un sensor STEP_COUNTER. Lleva el móvil contigo. La primera lectura establece la base; reinicios y huecos se muestran explícitamente. La fuente y el objetivo quedan fijados durante la sesión. El cardio por tiempo es una elección alternativa expresa, desactivada inicialmente, y no convierte minutos en pasos.

Concentración requiere activar voluntariamente Accesibilidad. Solo se usan identificadores de paquete, sin leer contenidos de otras apps. Spotify es la única app externa de usuario permitida. Ajustes, llamadas, emergencias, teclado, desbloqueo y launcher siguen accesibles. Diez confirmaciones permiten abandonar; emergencia sale inmediatamente. Android permite revocar permisos, detener o desinstalar; no se promete un bloqueo absoluto.

Según la versión de Android y la fuente de instalación, el sistema puede pedir «Permitir ajustes restringidos» en la información de la app antes de habilitar Accesibilidad. Es una decisión manual del usuario, no un permiso que GymApp pueda concederse.

Notificaciones requieren permiso en Android 13 o superior. Los recordatorios se activan tras tres fechas naturales completas sin pesas: una última sesión el viernes genera el primer aviso el martes. Franja, intervalo, posposición y pausa son configurables. WorkManager, Doze y HyperOS pueden retrasar la entrega. No se piden alarmas exactas ni se alteran No molestar o batería. Los descansos son temporizadores de ejercicios, sin límite ni aviso por duración de sesión.

En POCO/HyperOS busca en Ajustes Accesibilidad, notificaciones, inicio automático y restricciones de batería para GymApp. Las rutas dependen de la versión instalada; se necesita probar con el teléfono y reloj reales. Consulta [integraciones y límites](docs/PLATFORM_NOTES.md).

## Datos y copias

El estado se guarda transaccionalmente en Room y las preferencias se incluyen en la misma transacción para restaurar un conjunto coherente. Cada cambio mantiene una copia local atómica con tres posiciones diarias rotatorias. La copia interna no protege frente a desinstalación, pérdida del teléfono o borrado de datos. Usa Exportar copia en Ajustes y elige una ubicación con el selector Android. Importar valida tamaño, versión, tipos, identidades, referencias y snapshots antes de reemplazar el estado. La app no sube copias automáticamente.

Las cantidades se pesan en crudo/seco o como se venden según el alimento. Los carbohidratos disponibles ya están normalizados; no se descuenta dos veces la fibra. La energía del origen es autoritativa. El menú de referencia es orientativo y editable, sin promesas clínicas ni resultados de peso/músculo. No hay ajuste restrictivo automático ni compensación por calorías del reloj.

Datos alimentarios de [USDA SR Legacy/FoodData Central](https://fdc.nal.usda.gov/data-documentation/) de dominio público y etiquetas identificadas en [sources.json](data/nutrition/sources.json). La rutina es una adaptación a los requisitos, no una reproducción literal de ACSM. Véanse [fuentes](docs/SOURCES.md), [decisiones técnicas](docs/TECH_DECISIONS.md) e [informe de entrega](docs/DELIVERY_REPORT.md).

No se ha seleccionado una licencia de software para el proyecto. La atribución de los datos no concede una licencia al código.
