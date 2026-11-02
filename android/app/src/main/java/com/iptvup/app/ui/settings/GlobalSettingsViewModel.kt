package com.iptvup.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalSettingsViewModel @Inject constructor(
    private val updateManager: UpdateManager
) : ViewModel() {

    val updateState = updateManager.state

    fun checkUpdates(manual: Boolean = false) {
        viewModelScope.launch { updateManager.check(manual) }
    }

    fun installUpdate() {
        viewModelScope.launch { updateManager.downloadAndInstall() }
    }
}
