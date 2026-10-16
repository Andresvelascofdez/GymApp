# Publicación autorizada

Destino exacto: https://github.com/Andresvelascofdez/GymApp
Remoto HTTPS: https://github.com/Andresvelascofdez/GymApp.git
Al preparar el paquete el repositorio era público, vacío y con main como rama predeterminada.
Volver a comprobar al ejecutar: puede haber cambiado. No publicar en otro repositorio.

## Preparar sin sobrescribir
- Si ya estás en un checkout de ese repositorio, conservar .git, estado local e historial.
- Si estás dentro de OTRO repositorio, no cambiar su origin: crear un checkout separado adecuado.
- Si la carpeta solo tiene este paquete y el remoto está vacío, inicializar git con main y añadir
  origin. Si el remoto tiene historial, recuperarlo antes de integrar; ante colisiones comparar y
  combinar. Nunca borrar el contenido remoto ni usar git reset --hard para hacer sitio al paquete.
- Si es necesario clonar en una carpeta temporal/sibling para integrar un remoto no vacío,
  copiar los archivos del encargo sin eliminar instrucciones existentes; trabajar en el checkout.
- Revisar git diff y cambios previamente existentes. El padre coordina staging/commit/push.

## Datos que se pueden publicar
Código, agentes, especificaciones sin perfil real, recetas/rutinas de referencia, tests con
datos sintéticos, CI y documentación. Los datos de alimentos USDA son de dominio público;
atribuir su origen. No agregar una licencia de software no elegida por el usuario: dejar
documentado que no se ha seleccionado una licencia del proyecto si el remoto no tiene una.

NO publicar .private/, .local/, local-artifacts/, exportaciones de usuario, bases, secretos,
claves de firma, APK personal, capturas personales ni conversaciones del usuario.
El ZIP de este paquete contiene perfil privado: NO subir el ZIP a GitHub ni a una release.
El perfil personal es un input LOCAL del build, no un ejemplo ni una fixture de test.

## Entrega y CI
1. Validar datos y pasar pruebas/build/lint. Crear workflow de GitHub Actions actual y compatible
   que construya y pruebe sin .private ni secretos. Instalar/verificar JDK y SDK requeridos.
2. Generar APK genérico y, localmente, APK personal con nombres distintos. Compilación de prueba
   debug firmada por herramienta sirve para instalación personal; no afirmar firma release propia.
3. Actualizar README y docs/DELIVERY_REPORT.md con datos REALES. Mantener registros locales con
   información sensible fuera de git; capturas públicas solo sintéticas.
4. Revisar staging explícito. Ejecutar python scripts/check_public_payload.py sobre el índice.
   No usar git add -f para archivos ignorados. No subir binarios personales como assets.
5. Si remoto sigue vacío, hacer commit y push a main. Si ya contiene trabajo, crear rama
   gymapp/implementation (o nombre no colisionante), push y PR, sin force push ni merge
   que sobrescriba cambios. La autorización cubre subir esta implementación, no borrar historial.
6. Confirmar SHA remoto mediante git ls-remote o API, comprobar el workflow de ESE commit,
   corregir fallos y actualizar hasta resultados válidos. Evitar esperas infinitas.
7. Informar enlace al commit/PR y APK genérico (artifact de CI si existe), ruta al APK personal
   local, verificaciones y pendientes de hardware.

Usar autenticación ya configurada en git/gh/conector. Si no hay permisos de escritura, terminar
la app y preparar commits antes de la única respuesta final indicando cómo autenticar. No
pedir al usuario pegar un token en el chat, no leerlo en voz alta ni guardarlo en el repositorio.

La entrega de este paquete NO es ya una subida de la aplicación: el push se realiza cuando
el agente termine la construcción según estas instrucciones.
