package com.jarvis.interfaceapp.ui

import androidx.lifecycle.ViewModel
import com.jarvis.interfaceapp.domain.HomeAction
import com.jarvis.interfaceapp.domain.HomeState
import com.jarvis.interfaceapp.domain.reduceHome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    fun dispatch(action: HomeAction) { mutableState.update { reduceHome(it, action) } }
}
