package com.example.myapplication.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.state.MapState

@Composable
fun SearchBar(
    state: MapState,
    permissionLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.textCautat,
            onValueChange = { state.textCautat = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state.baraEsteFocusata = it.isFocused },
            placeholder = { Text("Caută...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Căutare") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    state.efectueazaCautarea(state.textCautat, permissionLauncher)
                }
            )
        )

        // Lista cu istoricul
        AnimatedVisibility(visible = state.baraEsteFocusata && state.istoricCautari.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    state.istoricCautari.forEach { cautareVeche ->
                        ListItem(
                            headlineContent = { Text(cautareVeche) },
                            leadingContent = { Icon(Icons.Default.History, contentDescription = "Istoric", tint = Color.Gray) },
                            modifier = Modifier.clickable {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                state.efectueazaCautarea(cautareVeche, permissionLauncher)
                            }
                        )
                    }

                    HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)

                    TextButton(
                        onClick = {
                            state.stergeIstoric()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Șterge istoricul", color = Color.Red)
                    }
                }
            }
        }
    }
}
