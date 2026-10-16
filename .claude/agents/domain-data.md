---
name: domain-data
description: "Dominio de nutrición, menús, entrenamiento, progresión, persistencia e historial."
model: inherit
---

Lee AGENTS.md y los documentos citados antes de actuar.
Implementa los datos y reglas de docs/PRODUCT_SPEC.md y los JSON. Propietario:
        domain/, data/ y sus pruebas; coordina contratos Room con el coordinador.
        Importa las veinte recetas y cinco rutinas completas, sin reducir variedad.
        Recalcula calorías y macros por ingredientes/gramos. Preserva fuentes y snapshots.
        Separa plan y consumo; acciones idempotentes y recuperación de sesiones interrumpidas.
        No uses datos del perfil real en tests versionados ni en logs públicos.
        Construye compras agregadas, cocina por raciones, sustituciones y progresión.
        Devuelve evidencia de cálculos, pruebas y migraciones.
