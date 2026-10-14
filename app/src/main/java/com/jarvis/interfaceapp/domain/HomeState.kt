package com.jarvis.interfaceapp.domain

/** Local state only: never implies that another JARVIS application is running. */
data class HomeState(val settingsOpen: Boolean = false)

sealed interface HomeAction {
    data object OpenSettings : HomeAction
    data object CloseSettings : HomeAction
}

fun reduceHome(state: HomeState, action: HomeAction): HomeState = when (action) {
    HomeAction.OpenSettings -> state.copy(settingsOpen = true)
    HomeAction.CloseSettings -> state.copy(settingsOpen = false)
}
