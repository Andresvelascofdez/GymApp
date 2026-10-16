# Capacidades Android: implementar y medir, no prometer

## Plataforma y entorno
POCO X8 Pro Max fue publicado con HyperOS 3 / Android 16. La versión instalada debe comprobarse
si hay adb disponible. No se ha probado físicamente este paquete en ese móvil.
El coordinador debe verificar documentación oficial actual y compatibilidad de SDK/AGP/Kotlin.
No desbloquear bootloader, usar root, convertir el móvil en DPC/device owner o cambiar ROM.

## Health Connect y Mi Fitness
Implementar el cliente oficial Health Connect con disponibilidad y permisos de lectura de pasos.
Mi Fitness anuncia compatibilidad, pero los tipos compartidos y la latencia dependen de la
versión/modelo. Detectar si hay registros/fuente; no afirmar que están llegando si no existen.
No existe aquí una API directa confirmada de Xiaomi ni un SDK de reloj seleccionado.
Leer un agregado acotado al inicio y ahora; comprobar las reglas de deduplicación/orígenes
de la versión oficial. No sumar registros solapados. Si se fija un origen concreto, hacerlo
explícito y no volver a sumar los pasos del teléfono. Respetar límites de acceso en background.
Los lotes que cruzan el inicio pueden requerir prorrateo del proveedor: explicar incertidumbre,
no prometer exactitud al paso del reloj. Preferir sensor del móvil para respuesta rápida si el
usuario lo elige y lleva el teléfono consigo. No agregar ambas fuentes al contador.

## Sensor del teléfono
Verificar sensor STEP_COUNTER / STEP_DETECTOR y ACTIVITY_RECOGNITION según API.
STEP_COUNTER es acumulado desde reinicio, no desde sesión: guardar baseline y manejar reset,
reinicio, suspensión y persistencia sin valores negativos o doble conteo. Un gap de sensor tras
reinicio debe mostrarse; reconstruir desde Health Connect solo si se puede reconciliar.
Usar el tipo de servicio en primer plano y permisos realmente requeridos por la versión objetivo.
No escuchar en bucle agresivo ni prometer tiempo real mientras Android tenga el servicio detenido.

## Bloqueador voluntario
Prototipo técnico real temprano: servicio de Accesibilidad con eventos de ventana apropiados,
estado persistido y una capa de bloqueo asociada a una app restringida en foreground.
Evitar leer textos/contenido de otras apps; el identificador de paquete basta para esta finalidad.
No solicitar captura de teclas, mensajes, contactos, archivos ajenos ni acceso remoto.
Comprobar Spotify permitido y varias apps sintéticas restringidas. Ignorar componentes esenciales
del sistema y no cubrir un flujo de emergencia o concesión/revocación de permisos.
No confundir fijación de pantalla, que restringe también cambiar a Spotify, con el modo selectivo.
No interceptar Inicio como si fuera un botón de la Activity. Una salida al launcher puede existir;
el objetivo es impedir el uso de apps restringidas, no impedir físicamente abandonar toda pantalla.
Accesibilidad es revocable. Mostrar advertencia funcional si no está activa; no presentar el modo
como funcionando con un boolean local. Verificar política Play si se plantea distribuir en tienda;
esta entrega es código y APK de instalación personal, no publicación automática en Google Play.

## Avisos y batería
Pedir permiso de notificaciones cuando corresponda. Elegir WorkManager/AlarmManager según la
necesidad y límites actuales: no pedir alarmas exactas para fingir que las notificaciones son
ineludibles. Mantener cálculo de próxima ventana, cambios de zona/DST y reinicio.
Guía de configuración de HyperOS: accesibilidad, notificaciones, inicio automático y batería.
Las rutas deben comprobarse por versión; proporcionar buscador de Ajustes si no hay deep link.
Ningún código otorga permisos de Android en nombre del usuario. Los tests emulados no demuestran
que el fabricante mantiene un servicio vivo en el POCO.

## Salidas y fallos
Emergencia directa sin diez confirmaciones. Fallo de sincronización separa pesas realizadas de
pasos no verificados. Caída del servicio o permiso retirado cambia estado visible y conserva datos.
Nada de apagado, manipulación del menú de encendido o bloqueo del dispositivo como administrador.

Ver docs/SOURCES.md y registrar en docs/TECH_DECISIONS.md las APIs/versiones realmente utilizadas.
