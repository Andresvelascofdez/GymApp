# Plan de ejecución completo

## Fase 0 — asumir el trabajo y comprobar entorno
Lee AGENTS y archivos. Ejecuta validate_brief.py. Identifica git raíz/origen, cambios locales,
JDK, Gradle, SDK, adb/emulador y autenticación disponible SIN imprimir tokens o variables secretas.
Si falta toolchain y la red lo permite, instala componentes acotados al proyecto/entorno.
No exijas Android Studio si bastan SDK CLI y JDK. No inventes que se ha compilado si faltan.
Crea .local/WORK_LOG.md para progreso y docs/TECH_DECISIONS.md sin perfil real.

## Fase 1 — contratos, proyecto y primer build
Define modelos, ownership y estados. Genera proyecto nativo, wrapper, versiones compatibles,
manifiesto y navegación. Construye una app mínima instalable. Prepara CI Android para ejecutar
los mismos comandos. No subas un scaffold como producto terminado.

## Fase 2 — dominios y prueba de capacidades
En paralelo si hay agentes: datos/dominio, plataforma Android y UI sobre contratos acordados.
Primero demostrar bloqueo selectivo y lectura de pasos reales en superficies disponibles.
Si no existe móvil/reloj, implementar adaptadores reales más pruebas de contrato/denegación;
documentar lo que no se puede probar. No reemplazar adaptadores por simulaciones silenciosas.

## Fase 3 — integración funcional
Completar registro de series, reanudación, temporizador de descansos y progresión; comidas,
gramos, recetas, compras, preparación, sustituciones; recordatorios, concentración, pasos;
progreso, bienestar y copias. Importar todos los datos JSON con IDs/versiones estables.
Crear el APK personal local a partir del perfil privado sin contaminar el build genérico.

## Fase 4 — QA y correcciones
Revisor independiente si disponible. Validar todos los AC aplicables. Unit tests de dominio,
Room/integración, UI Compose, build, lint y emulador. Revisar textos españoles, modo oscuro,
permisos faltantes, pantallas pequeñas, números, accesibilidad, proceso muerto y offline.
Corregir, repetir comprobaciones afectadas y registrar evidencia real. No sustituir pruebas por
una lista de cosas que habría que probar. Hacer revisión final de ausencia de funciones retiradas.

## Fase 5 — entrega
Generar APK genérico y personal local con nombres inequívocos; sin claves de firma privadas en git.
README de instalación y guía HyperOS. Crear informe de entrega desde la plantilla con resultados
y pasos físicos pendientes. Revisar publicación y ejecutar el procedimiento GitHub autorizado.
Confirmar remoto/commit, esperar CI con plazos razonables y corregir fallos atribuibles al proyecto.
Solo entonces responder al usuario, una vez. Si un bloqueo externo persiste, entregar el resto
con estado real, causa y la acción mínima necesaria; no afirmar finalización total falsa.

No dejar TODOs funcionales, mocks de producción, pantallas sin acciones o fallos conocidos sin
tratamiento bajo la etiqueta «MVP». Se puede documentar una limitación REAL de plataforma.
