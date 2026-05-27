package com.example.fibraconet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fibraconet.BuildConfig
import com.example.fibraconet.data.model.Channel
import com.example.fibraconet.data.model.ChannelGroup
import com.example.fibraconet.data.model.LoginCredentials
import com.example.fibraconet.data.repository.M3URepository
import com.example.fibraconet.data.repository.PreferencesRepository
import com.example.fibraconet.data.repository.UpdateInfo
import com.example.fibraconet.data.repository.UpdateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed class UiState {
    object Idle    : UiState()
    object Loading : UiState()
    data class Success(val groups: List<ChannelGroup>, val allChannels: List<Channel>) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class UpdateState {
    object Idle : UpdateState()
    data class NewVersionAvailable(val updateInfo: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val m3uRepo   = M3URepository()
    private val prefsRepo = PreferencesRepository(application)
    private val updateManager = UpdateManager()

    private val _uiState    = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _credentials = MutableStateFlow<LoginCredentials?>(null)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val favorites: StateFlow<Set<String>> = prefsRepo.getFavoritesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val recentChannelIds: StateFlow<List<String>> = prefsRepo.getRecentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = combine(recentChannelIds, uiState) { ids, state ->
        val allCh = (state as? UiState.Success)?.allChannels ?: return@combine emptyList()
        ids.mapNotNull { id -> allCh.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val savedCreds = prefsRepo.getSavedCredentials()
            val savedM3U   = prefsRepo.getSavedM3UUrl()
            when {
                savedCreds != null -> {
                    _credentials.value = savedCreds
                    _isLoggedIn.value  = true
                    fetchM3U(savedCreds)
                }
                savedM3U != null -> {
                    _isLoggedIn.value = true
                    loadM3UFromUrl(savedM3U)
                }
                else -> {
                    _uiState.value = UiState.Idle
                }
            }
            // Comprobación automática de actualizaciones en segundo plano al iniciar
            checkForUpdates()
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        val creds = LoginCredentials(serverUrl.trimEnd('/'), username.trim(), password)
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            m3uRepo.fetchPlaylist(creds).fold(
                onSuccess = { channels ->
                    if (channels.isEmpty()) {
                        _uiState.value = UiState.Error("No se encontraron canales en la lista")
                    } else {
                        prefsRepo.saveCredentials(creds)
                        _credentials.value = creds
                        _isLoggedIn.value  = true
                        _uiState.value     = UiState.Success(m3uRepo.groupChannels(channels), channels)
                    }
                },
                onFailure = { e -> _uiState.value = UiState.Error(e.message ?: "Error desconocido") }
            )
        }
    }

    fun loadM3UFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            m3uRepo.fetchFromUrl(url).fold(
                onSuccess = { channels ->
                    if (channels.isEmpty()) {
                        _uiState.value = UiState.Error("No se encontraron canales en la lista")
                    } else {
                        prefsRepo.saveM3UUrl(url)
                        _isLoggedIn.value = true
                        _uiState.value    = UiState.Success(m3uRepo.groupChannels(channels), channels)
                    }
                },
                onFailure = { e -> _uiState.value = UiState.Error(e.message ?: "Error desconocido") }
            )
        }
    }

    fun fetchM3U(creds: LoginCredentials? = _credentials.value) {
        if (creds == null) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            m3uRepo.fetchPlaylist(creds).fold(
                onSuccess = { channels ->
                    _uiState.value = if (channels.isEmpty())
                        UiState.Error("No se encontraron canales")
                    else
                        UiState.Success(m3uRepo.groupChannels(channels), channels)
                },
                onFailure = { e -> _uiState.value = UiState.Error(e.message ?: "Error desconocido") }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefsRepo.clearCredentials()
            _credentials.value    = null
            _currentChannel.value = null
            _isLoggedIn.value     = false
            _uiState.value        = UiState.Idle
        }
    }

    fun selectChannel(channel: Channel) {
        _currentChannel.value = channel
        viewModelScope.launch { prefsRepo.addRecentChannel(channel.id) }
    }

    fun clearChannel() { _currentChannel.value = null }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch { prefsRepo.toggleFavorite(channelId) }
    }

    fun getFavoriteChannels(): List<Channel> {
        val state = _uiState.value as? UiState.Success ?: return emptyList()
        return state.allChannels.filter { favorites.value.contains(it.id) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val updateInfo = updateManager.checkForUpdates(BuildConfig.VERSION_CODE)
            if (updateInfo != null) {
                _updateState.value = UpdateState.NewVersionAvailable(updateInfo)
            } else {
                _updateState.value = UpdateState.Idle
            }
        }
    }

    fun startDownload(apkUrl: String) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0f)
            val context = getApplication<Application>().applicationContext
            val destinationFile = File(context.cacheDir, "update.apk")

            val success = updateManager.downloadApk(apkUrl, destinationFile) { progress ->
                _updateState.value = UpdateState.Downloading(progress)
            }

            if (success) {
                _updateState.value = UpdateState.ReadyToInstall(destinationFile)
            } else {
                _updateState.value = UpdateState.Error("Fallo al descargar el archivo de actualización.")
            }
        }
    }

    fun installUpdate(apkFile: File) {
        val context = getApplication<Application>().applicationContext
        updateManager.installApk(context, apkFile)
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }
}
