package com.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymapp.ui.GymTheme

class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            GymTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Privacidad y pasos", style = MaterialTheme.typography.headlineMedium)
                        Text("GymApp solicita únicamente lectura de pasos de Health Connect para medir el objetivo desde que empiezas cada entrenamiento. No escribe datos de salud ni consulta periodos anteriores a la sesión.")
                        Text("Las consultas se realizan mientras usas la app. Health Connect combina las fuentes según tus prioridades. Los registros de Mi Fitness pueden llegar con retraso; no existe una conexión directa con el reloj.")
                        Text("Los resultados y tu historial se guardan localmente. No hay cuentas, publicidad, analítica ni envío a servidores. Solo se exportan si eliges un archivo con el selector de Android.")
                        Text("Puedes retirar el permiso desde Health Connect o Ajustes. Entrenamiento y comidas siguen disponibles. Si el sensor falla, puedes cerrar la sesión con ese motivo o salir por emergencia.")
                        Button(onClick = { finish() }) { Text("Volver") }
                    }
                }
            }
        }
    }
}
