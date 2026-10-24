package com.jarvis.interfaceapp.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class PersonaSettingsViewModel:ViewModel() {
    val controller=IdentitySettingsController(viewModelScope)
    override fun onCleared() { controller.close() }
}
