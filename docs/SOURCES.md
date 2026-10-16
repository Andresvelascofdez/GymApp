# Fuentes de referencia

Consultadas al preparar el paquete: 2026-10-16. El implementador debe verificar API/versiones
actuales antes de desarrollar. La rutina concreta es una adaptación a los requisitos; no
pretende ser una rutina literal publicada por ACSM. Menús calculados por ingredientes,
sin prometer adecuación clínica individual ni resultados concretos.

## Entrenamiento y alimentación
- ACSM, recomendaciones 2026: https://acsm.org/resistance-training-guidelines-update-2026/
- ISSN, proteína y ejercicio: https://link.springer.com/article/10.1186/s12970-017-0177-8
- NIDDK, actividad a cualquier tamaño: https://www.niddk.nih.gov/health-information/weight-management/staying-active-at-any-size
- USDA FoodData Central: https://fdc.nal.usda.gov/data-documentation/
- USDA SR Legacy (datos descargados para cálculo): https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR-Legacy/SR-Leg_ASC.zip
- Conservación: https://www.foodsafety.gov/food-safety-charts/cold-food-storage-charts
- Las etiquetas concretas están enlazadas en data/nutrition/sources.json.

## Android
- Health Connect lectura: https://developer.android.com/health-and-fitness/health-connect/read-data
- Sensores: https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
- Accesibilidad: https://developer.android.com/guide/topics/ui/accessibility/views/service
- Ventanas: https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo
- Notificaciones: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Lock task / pinning y límites: https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
- Mi Fitness: https://play.google.com/store/apps/details?id=com.xiaomi.wearable
- POCO: https://www.mi.com/global/support/faq/details/KA-676346/

## Archivos de agentes
- Codex AGENTS.md: https://learn.chatgpt.com/docs/agent-configuration/agents-md
- Codex subagents: https://learn.chatgpt.com/docs/agent-configuration/subagents
- Claude subagents: https://code.claude.com/docs/en/sub-agents
- Claude CLAUDE.md: https://code.claude.com/docs/en/memory

Codex: TOML autónomo con name, description y developer_instructions; modelo omitido para heredar.
Claude Code: Markdown con frontmatter y model: inherit; CLAUDE.md importa AGENTS.md.
Versiones anteriores pueden necesitar adaptar la sintaxis; mantener los roles y continuar.
