# Criterios de aceptación

- **AC01** — Build Android nativo reproducible con Wrapper; APK genérico instalable; no web/PWA.
- **AC02** — Tema oscuro inicial, navegación completa y español, con acciones reales en las cinco pantallas.
- **AC03** — Los cinco días tienen las cuatro comidas completas y las veinte recetas incluidas.
- **AC04** — Recalcular desde ingredientes reproduce kcal/proteína de referencia dentro del redondeo; día de referencia 2.000–2.200 kcal y 190–215 g de proteína.
- **AC05** — Editar gramos cambia macros y total; marcar/desmarcar es idempotente; el plan no se contabiliza como consumo antes de marcar.
- **AC06** — Historial nutricional conserva snapshot al cambiar ingredientes/etiquetas del catálogo.
- **AC07** — Compras agregan cantidades por alimento y raciones; fines de semana libres; valores ausentes no se convierten en cero.
- **AC08** — Se pueden sustituir platos y registrar alimentos propios sin red; el nuevo total es visible.
- **AC09** — Hay cinco rutinas completas con ejercicios, series, rangos, descansos y alternativas; sin zancadas/crunch obligatorio.
- **AC10** — Series registrables y editables, pesos anteriores reales, calentamientos separados y progresión justificada.
- **AC11** — Un día perdido no salta la sesión pendiente; modo abreviado se guarda como abreviado.
- **AC12** — Matar/reabrir el proceso conserva UUID de sesión, series, inicio, progreso y contador sin duplicar.
- **AC13** — Solo cuentan pasos posteriores al inicio; contador por sesión persiste y no incluye los previos del día.
- **AC14** — Health Connect no suma fuentes solapadas y el móvil/reloj no se agregan dos veces; pruebas de datos tardíos y cambio de fuente.
- **AC15** — Sensor ausente/permiso denegado/reinicio tienen estados explícitos; no negativos, pasos inventados ni bloqueo sin salida por fallo.
- **AC16** — Mostrar fuente y última sincronización reales; no afirmar Mi Fitness conectada sin registros/evidencia.
- **AC17** — Pesas realizadas cancela ausencia; pasos pendientes solo genera aviso de cardio; cero series no cuenta como entrenamiento.
- **AC18** — En concentración Spotify es la única app externa de usuario permitida, incluida prueba de apertura de apps restringidas.
- **AC19** — Permiso de Accesibilidad retirado o servicio parado se refleja como protección inactiva; no boolean ficticio.
- **AC20** — Abandono normal tiene diez confirmaciones y cancelar restaura la sesión; emergencia sale directamente.
- **AC21** — Emergencias/componentes esenciales del sistema permanecen accesibles; ajustes y revocación de permisos no se secuestran.
- **AC22** — NO hay límite de sesión, avisos de duración, apagado, bloqueo nocturno o reconocimiento de comida por foto.
- **AC23** — Última sesión viernes produce primer aviso martes tras tres fechas completas; probar también sin historial y cambio de zona/DST.
- **AC24** — Recordatorios respetan franja, aplazamiento, pausa y permisos; no ráfaga acumulada tras reinicio o fin de pausa.
- **AC25** — Calendario de recordatorios y acciones funcionan offline con tolerancia de plataforma documentada, sin depender de push de servidor.
- **AC26** — Peso/media semanal, hambre/energía/rendimiento opcionales y progreso guardados sin diagnósticos ni restricciones automáticas.
- **AC27** — Exportación/importación con selector, versión, validación, transacción y round-trip sin duplicar/perder entidades.
- **AC28** — El build genérico/CI y git están libres del perfil real, secretos y bases de usuario; el APK personal permanece local.
- **AC29** — Unit tests y lint/build ejecutados con resultados guardados; UI/instrumentación en emulador si existe; explicar NOT_RUN.
- **AC30** — GitHub recibe el commit correcto sin force push, CI se revisa y README contiene instalación, permisos y límites.
- **AC31** — Prueba física de supervivencia del servicio en POCO/HyperOS y recepción de pasos del reloj; solo PASS con dispositivo real.
- **AC32** — No hay backend, API de IA, token de modelo, cuenta obligatoria ni telemetría; core funciona sin conexión.

Para cada AC indicar PASS / FAIL / NOT_RUN y evidencia. AC31 requiere hardware físico.
No convertir NOT_RUN en PASS ni detener toda la implementación por AC31.
