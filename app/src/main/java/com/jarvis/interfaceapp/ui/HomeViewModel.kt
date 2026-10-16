package com.jarvis.interfaceapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jarvis.interfaceapp.domain.HomeAction

class HomeViewModel(private val controller: DashboardController) : ViewModel() {
    val state = controller.state
    init { controller.startSession() }
    fun dispatch(action: HomeAction) = controller.dispatch(action)
    fun resetLayouts() = controller.reset()
    fun retryStorage() = controller.retry()

    class Factory(private val controller: DashboardController) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(controller) as T
        }
    }
}
