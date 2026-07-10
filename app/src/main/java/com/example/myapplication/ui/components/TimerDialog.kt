package com.example.myapplication.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.state.MapState

@Composable
fun TimerDialog(
    state: MapState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setează ora sosirii") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.inputOra,
                    onValueChange = { state.inputOra = it },
                    label = { Text("Ora (0-23)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.inputMinut,
                    onValueChange = { state.inputMinut = it },
                    label = { Text("Minut (0-59)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = {
                        val secunde = state.calculeazaTimpDisponibil()
                        if (secunde > 0) {
                            state.safeZoneManager.circleRadiusMeters = (secunde / 60.0) * 83.0 // Mers pe jos
                            state.safeZoneManager.isSafeZoneModeActive = true
                            state.drivingSimulator.isDrivingModeActive = false
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Ora introdusă a trecut sau e invalidă!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Safe Zone (Cerc)")
                    Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = null)
                }

                Button(
                    onClick = {
                        val secunde = state.calculeazaTimpDisponibil()
                        if (secunde > 0 && state.traseuGeoJson != null) {
                            state.timpDisponibilSecunde = secunde
                            state.drivingSimulator.isDrivingModeActive = true
                            state.safeZoneManager.isSafeZoneModeActive = false
                            onDismiss()
                        } else if (state.traseuGeoJson == null) {
                            Toast.makeText(context, "Caută o destinație mai întâi!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ora introdusă a trecut!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Start Driving (Punct animat)")
                    Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null)
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) { Text("Anulează") }
        }
    )
}
