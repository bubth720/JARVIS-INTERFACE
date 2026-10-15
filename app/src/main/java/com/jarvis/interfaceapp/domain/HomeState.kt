package com.jarvis.interfaceapp.domain

/** Local state only: never implies that another JARVIS application is running. */
data class HomeState(
    val settingsOpen: Boolean = false,
    val editing: Boolean = false,
    val layouts: Map<DashboardProfile, List<DashboardPanel>> =
        DashboardProfile.entries.associateWith(::initialPanels),
    val placementError: PlacementError? = null,
)

sealed interface HomeAction {
    data object OpenSettings : HomeAction
    data object CloseSettings : HomeAction
    data object ToggleEditing : HomeAction
    data object ExitEditing : HomeAction
    data class ToggleLock(val profile: DashboardProfile, val id: PanelId) : HomeAction
    data class Place(val profile: DashboardProfile, val id: PanelId, val x: Int, val y: Int,
        val width: Int, val height: Int) : HomeAction
}

fun reduceHome(state: HomeState, action: HomeAction): HomeState = when (action) {
    HomeAction.OpenSettings -> state.copy(settingsOpen = true, editing = false, placementError = null)
    HomeAction.CloseSettings -> state.copy(settingsOpen = false)
    HomeAction.ToggleEditing -> if (state.settingsOpen) state else
        state.copy(editing = !state.editing, placementError = null)
    HomeAction.ExitEditing -> state.copy(editing = false, placementError = null)
    is HomeAction.ToggleLock -> if (!state.editing || state.settingsOpen) state else {
        val panels = state.layouts.getValue(action.profile).map {
            if (it.id == action.id) it.copy(locked = !it.locked) else it
        }
        state.copy(layouts = state.layouts + (action.profile to panels), placementError = null)
    }
    is HomeAction.Place -> if (!state.editing || state.settingsOpen) state else {
        val result = placePanel(action.profile.grid, state.layouts.getValue(action.profile),
            action.id, action.x, action.y, action.width, action.height)
        state.copy(layouts = state.layouts + (action.profile to result.panels), placementError = result.error)
    }
}
