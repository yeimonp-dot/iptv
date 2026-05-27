package com.example.fibraconet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fibraconet.R
import com.example.fibraconet.ui.MainViewModel
import com.example.fibraconet.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var serverUrl by remember { mutableStateOf("http://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var useM3UUrl by remember { mutableStateOf(false) }
    var m3uUrl by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) onLoginSuccess()
    }

    // Fondo con gradiente animado
    val infiniteTransition = rememberInfiniteTransition(label = "loginBg")
    val bgAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgAnim"
    )
    val bgTop    = lerp(Color(0xFF0D1B2A), Color(0xFF091520), bgAnim)
    val bgMid    = lerp(Color(0xFF1B2838), Color(0xFF152030), bgAnim)
    val bgBottom = lerp(Color(0xFF0D1B2A), Color(0xFF0A1825), bgAnim)

    // Animación de entrada del card
    val cardOffsetY by animateFloatAsState(
        targetValue = 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardEntry"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgMid, bgBottom)))
    ) {
        // Destellos de color decorativos
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-60).dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF00D4FF).copy(alpha = 0.06f), Color.Transparent))
                )
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 40.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF6B00FF).copy(alpha = 0.07f), Color.Transparent))
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono + título
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2D3D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "FIBRACONET",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 3.sp
            )
            Text(
                text = "Reproductor IPTV",
                fontSize = 13.sp,
                color = Color(0xFF00D4FF),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Card de login
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .offset(y = cardOffsetY.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F2E)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Iniciar sesión",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )

                    // Toggle Xtream / M3U
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0D1B2A))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(false to "Xtream Codes", true to "URL M3U").forEach { (isM3U, label) ->
                            val selected = useM3UUrl == isM3U
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .border(
                                        width = if (isFocused) 2.dp else 0.dp,
                                        color = if (isFocused) Color(0xFF00D4FF) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        if (selected) Color(0xFF1E2D3D)
                                        else if (isFocused) Color(0x2200D4FF)
                                        else Color.Transparent
                                    )
                                    .clickable { useM3UUrl = isM3U }
                                    .focusable()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color(0xFF00D4FF) else if (isFocused) Color.White else Color(0xFF8899AA),
                                    fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (useM3UUrl) {
                        val urlMissingProtocol = m3uUrl.isNotBlank() &&
                            !m3uUrl.trimStart().startsWith("http://") &&
                            !m3uUrl.trimStart().startsWith("https://")
                        OutlinedTextField(
                            value = m3uUrl,
                            onValueChange = { m3uUrl = it },
                            label = { Text("URL del archivo M3U") },
                            placeholder = { Text("http://servidor.com/lista.m3u") },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            supportingText = if (urlMissingProtocol) {
                                { Text("La URL debe comenzar con http:// o https://", color = Color(0xFFFFAA44)) }
                            } else null,
                            isError = urlMissingProtocol,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (m3uUrl.isNotBlank()) viewModel.loadM3UFromUrl(m3uUrl.trim())
                                }
                            ),
                            colors = loginTextFieldColors()
                        )
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("URL del Servidor") },
                            placeholder = { Text("http://api.ejemplo.com:8000") },
                            leadingIcon = { Icon(Icons.Default.Dns, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = loginTextFieldColors()
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Usuario Asignado") },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = loginTextFieldColors()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (serverUrl.isNotBlank() && username.isNotBlank())
                                        viewModel.login(serverUrl, username, password)
                                }
                            ),
                            colors = loginTextFieldColors()
                        )
                    }

                    // Error
                    if (uiState is UiState.Error) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x22FF4444))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, null, tint = Color(0xFFFF6666), modifier = Modifier.size(18.dp))
                            Text(
                                text = (uiState as UiState.Error).message,
                                color = Color(0xFFFF9999),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Botón
                    var buttonFocused by remember { mutableStateOf(false) }
                    val buttonScale by animateFloatAsState(if (buttonFocused) 1.04f else 1f, label = "btnScale")
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (useM3UUrl) {
                                if (m3uUrl.isNotBlank()) viewModel.loadM3UFromUrl(m3uUrl.trim())
                            } else {
                                if (serverUrl.isNotBlank() && username.isNotBlank())
                                    viewModel.login(serverUrl, username, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .scale(buttonScale)
                            .onFocusChanged { buttonFocused = it.isFocused }
                            .then(
                                if (buttonFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                else Modifier
                            ),
                        enabled = uiState !is UiState.Loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFF00C8F0), Color(0xFF00D4FF), Color(0xFF00C0E8))),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState is UiState.Loading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0D1B2A), strokeWidth = 2.dp)
                                    Text("Conectando...", color = Color(0xFF0D1B2A), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF0D1B2A), modifier = Modifier.size(20.dp))
                                    Text("Ingresar", color = Color(0xFF0D1B2A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "v1.0 — Fibraconet IPTV Player",
                fontSize = 11.sp,
                color = Color(0xFF334455)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Color(0xFF00D4FF),
    unfocusedBorderColor    = Color(0xFF1E2D3D),
    focusedLabelColor       = Color(0xFF00D4FF),
    unfocusedLabelColor     = Color(0xFF8899AA),
    cursorColor             = Color(0xFF00D4FF),
    focusedTextColor        = Color.White,
    unfocusedTextColor      = Color(0xFFCCDDEE),
    focusedLeadingIconColor = Color(0xFF00D4FF),
    unfocusedLeadingIconColor  = Color(0xFF8899AA),
    focusedTrailingIconColor   = Color(0xFF00D4FF),
    unfocusedTrailingIconColor = Color(0xFF8899AA),
    focusedContainerColor      = Color(0xFF0D1B2A),
    unfocusedContainerColor    = Color(0xFF0D1B2A),
)
