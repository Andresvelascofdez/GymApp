# Especificación cerrada — GymApp

## Producto
Aplicación personal Android nativa en español para dieta, fuerza y constancia. Tema oscuro.
Uso principal: POCO X8 Pro Max; validar versión instalada de HyperOS/Android al probar.
Reloj Xiaomi emparejado con Mi Fitness; modelo por descubrir sin detener la implementación.
Perfil real en .private/profile.local.json; leer localmente, nunca publicar sus valores.

Objetivos nutricionales iniciales configurables: 2.000–2.200 kcal/día y alrededor de 200 g
de proteína. El menú incluido ronda 2.150–2.200 kcal; no exige llegar a 200 g exactos.
No se prometen pérdidas de peso ni ganancias musculares concretas. Sin ajuste automático
restrictivo de calorías, ni compensación automática por calorías «quemadas» del reloj.

## Navegación
Cinco destinos: Hoy, Entrenar, Comidas, Progreso y Ajustes.
Hoy: próxima sesión, botón grande «Empezar entrenamiento», cuatro comidas del día,
consumo frente al objetivo, sesión pendiente y estado de permisos/sincronización.
Los datos desconocidos son «Sin datos»; no números inventados.

## Comidas
- Lunes a viernes: cinco menús completos y variados en data/nutrition/week_plan.json.
- Cuatro slots: desayuno, comida, cena y snack. Snack configurable mañana o merienda.
- Sábado/domingo: sin menú obligatorio, con registro opcional. Días sin registro NO son cero kcal.
- Cada plato muestra ingredientes, gramos, preparación y kcal/proteína/grasas/carbohidratos/fibra.
- Todo alimento cocinable se pesa antes de cocinar; arroz/pasta/lentejas secos. Fruta sin piel
  no comestible, huevos sin cáscara, gambas peladas. Lácteos/pan/tortillas como se venden.
- Marcar una comida del plan con un toque; editar cantidades antes/después; deshacer. No duplicar
  consumos por doble toque. Planificado y consumido son entidades distintas.
- Registrar también alimentos fuera del plan desde la base local y crear alimentos propios
  con datos de etiqueta por 100 g. Sin servicio externo obligatorio ni escáner fotográfico.
- Sustituir platos/alimentos, ajustar raciones y recalcular el día, mostrando el nuevo total.
  No afirmar equivalencia de macros si el cambio no la cumple; proponer cantidades orientativas.
- Lista de compra agregada según días y raciones; unidades normalizadas, editable y marcable.
- Cocina por tandas: escalar cantidades en crudo por número de raciones y repartir porciones.
  Guardar/favoritos/repetir otra fecha. Recetas con instrucciones y nota de conservación.
- Los cambios de receta/etiqueta crean una versión nueva: lo consumido antes conserva snapshots.
- La energía publicada en la etiqueta/base es autoritativa. No forzar igualdad exacta con 4/4/9:
  factores específicos, fibra y redondeos pueden explicar diferencias.
- En el JSON los carbohidratos disponibles se normalizan: USDA total menos fibra; etiquetas UE
  ya indican disponibles. No descontar fibra dos veces. Mantener metadatos de origen.

## Entrenamiento
- Cinco sesiones ordenadas: empuje, tirón, piernas A, torso, piernas B; datos en routine.json.
- Si falta un día, continuar la siguiente sesión pendiente; no saltar ejercicios al cambiar fecha.
- Rutina editable y ejercicios alternativos predefinidos con historial por ejercicio real.
- Registrar series, kilos, repeticiones, esfuerzo opcional y nota; distinguir calentamiento.
- Mostrar resultados anteriores y precargar pesos; nunca marcar una serie completada sin acción.
- Temporizador de descanso configurable por ejercicio; vibración/aviso al terminar. El temporizador
  de descanso NO es un límite a la sesión, que es ilimitada y no tiene avisos por duración.
- Sugerir progresión cuando todas las series alcancen el extremo alto con técnica/esfuerzo adecuados.
  Usuario acepta el incremento; redondear a su material y mantener opción de repetir peso.
- Modo «Hoy me cuesta»: plan abreviado explícito (primeros tres ejercicios, dos series cada uno,
  editable). Guardar como abreviado, no fingir rutina completa. Requiere registro de series reales.
- Guardado inmediato y recuperación tras cerrar app, muerte de proceso o reinicio.
- El usuario puede terminar pesas con ejercicios omitidos si confirma y registra el motivo.
  No computar cero series como sesión de pesas realizada.

## Pasos y cierre
- «Empezar entrenamiento» fija UUID, fecha/hora instantánea y referencia de fuente.
- 3.500 pasos mínimo DESDE ese instante, incluidos los que haga durante las pesas y el cardio.
- Nunca usar el total previo del día. Persistir inicio y estado; no reiniciar el objetivo al volver.
- Contador: realizados/restantes, fuente, última lectura y estado «pendiente de sincronizar».
- Health Connect preferido para datos compartidos por Mi Fitness; alternativa sensor del móvil
  cuando disponible y autorizado. Elegir una fuente/coherencia de agregado por sesión sin sumar
  reloj+móvil ni cambiar silenciosamente la base a mitad. No inventar Bluetooth/API del reloj.
- Al completar pesas, parar recordatorios de «no has ido», aunque queden pasos pendientes.
- Cierre normal completo requiere pesas y objetivo de pasos. Si hay dolor, urgencia o fallo real,
  salida explícita: incompleta / emergencia / fallo de sensor. Nunca forzar actividad ni inventar pasos.
- Cardio por tiempo (p. ej. bicicleta) configurable como objetivo alternativo por elección expresa;
  desactivado por defecto, no equivalente automáticamente a 3.500 pasos ni convierte minutos en pasos.

## Concentración
- Modo voluntario activado al iniciar: solo esta app y Spotify (com.spotify.music) entre apps de usuario.
- El usuario NO puede añadir libremente otras apps permitidas durante la sesión. Cambios de ajustes
  que relajen el bloqueo se aplican a futuras sesiones, salvo salida explícita de emergencia/abandono.
- Detectar apertura de app restringida con Accesibilidad y presentar barrera de interacción/retorno.
  Implementar, comprobar y explicar los límites reales; no equipararlo al Zen propietario.
- Mantener acceso de emergencia y las superficies esenciales del sistema (permisos, desbloqueo,
  llamadas entrantes y otras que Android exija). No bloquear Ajustes para impedir revocar permisos.
- Mi Fitness puede sincronizar EN SEGUNDO PLANO; no añadirla a la lista de apps utilizables.
- Abandono normal: diez confirmaciones sucesivas, progreso 1/10...10/10 y «Volver al entrenamiento».
  Cancelar vuelve a la sesión y reinicia la cadena. No bloquear la salida de emergencia con diálogos.
- Si Spotify no está instalado, mostrar estado sin abrir navegador/tienda durante concentración.
- Reanudación de servicio sujeta a reglas Android; no prometer protección absoluta contra desactivar,
  forzar detención, reiniciar o desinstalar. Un permiso revocado debe verse como modo no activo.

## Recordatorios
- Tras tres días naturales COMPLETOS sin sesión de pesas, activar el cuarto día en franja elegida.
  Ejemplo: última sesión viernes; sábado, domingo y lunes completos sin entrenar; primer aviso martes.
- Valores iniciales: cada 60 minutos, 06:00–21:00, zona local del teléfono. Configurables.
- Si no hay historial, contar desde el día de alta/importación, no tratar al usuario como meses ausente.
- Distintos mensajes directos, sin humillaciones. Botones: «Empezar» y «Posponer 30 min».
- Al comenzar, suspender avisos de ausencia durante la sesión. Abandonar sin series no reinicia la
  cuenta; cerrar una sesión válida/abreviada sí. Persistir aplazamientos y mostrarlos dentro de la app.
- Si solo faltan pasos, avisos específicos dentro de la misma franja; no duplicar canales de ausencia.
- Descansos semanales normales NO equivalen a pausa. Enfermedad/lesión/vacaciones permiten pausa
  explícita con fechas; no emitir avisos en ella, ni acumulados al salir.
- Respetar permisos, silencio/No molestar y restricciones de batería. Notificaciones locales;
  no servidor push obligatorio. No garantizar entrega exacta en Doze y documentar la tolerancia real.

## Progreso y datos
- Registro de peso, media móvil de siete días y tendencia semanal, con días sin medición diferenciados.
- Historial de entrenamiento, cargas, repeticiones, sesiones completas/abreviadas/incompletas.
- Valoración breve opcional de hambre, energía y rendimiento; sin diagnóstico ni cambios automáticos.
- Almacenamiento local; exportación/importación versionada de perfil, registros y preferencias
  usando selector Android. Validar formato, tamaño, tipos y referencias antes de reemplazar datos.
- Restaurar de forma transaccional, sin duplicar entradas. Copia local automática rotatoria más
  exportación a ubicación elegida; explicar que copiar dentro de la app no protege contra desinstalar.
- No analytics ni cuentas ni publicidad. No tokens, API keys o modelos de IA en la app.

## Fuera de alcance por decisión del usuario
Fotografía/estimación de calorías por imagen; apagado; bloqueo nocturno; límite de sesión;
avisos a 75/90 minutos; aplicaciones externas permitidas distintas de Spotify; IA generativa en runtime.
