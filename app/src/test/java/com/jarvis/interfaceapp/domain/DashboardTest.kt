package com.jarvis.interfaceapp.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DashboardTest {
    private val profile = DashboardProfile.COMPACT
    private val grid = profile.grid
    private val initial = initialPanels(profile)

    @Test fun bothProfilesHaveFourUniqueValidPanelsAndFreeSpace() {
        for (profile in DashboardProfile.entries) {
            val panels = initialPanels(profile)
            assertEquals(PanelId.entries.toSet(), panels.map { it.id }.toSet())
            assertEquals(4, panels.size)
            panels.forEach { assertNull(placementError(profile.grid, panels, it)) }
            assertTrue(panels.sumOf { it.width * it.height } < profile.grid.columns * profile.grid.rows)
        }
    }
    @Test fun moveIntoGapLeavesNeighboursUnchanged() {
        val result = placePanel(grid, initial, PanelId.AVATAR, y = 1)
        assertNull(result.error)
        assertEquals(1, result.panels.first().y)
        assertEquals(initial.drop(1), result.panels.drop(1))
        assertEquals(0, initial.first().y)
    }
    @Test fun collisionIsRefusedAtomically() {
        val result = placePanel(grid, initial, PanelId.AVATAR, x = 3)
        assertEquals(PlacementError.OVERLAP, result.error)
        assertEquals(initial, result.panels)
    }
    @Test fun outsideAnyEdgeIsRefused() {
        for ((x, y) in listOf(-1 to 0, 0 to -1, 4 to 0, 0 to 10)) {
            val result = placePanel(grid, initial, PanelId.AVATAR, x = x, y = y)
            assertEquals(PlacementError.OUT_OF_BOUNDS, result.error)
            assertEquals(initial, result.panels)
        }
    }
    @Test fun resizeIntoGapIsAllowed() {
        val result = placePanel(grid, initial, PanelId.AVATAR, height = 5)
        assertNull(result.error)
        assertEquals(5, result.panels.first().height)
    }
    @Test fun resizeIntoNeighbourIsRefused() {
        assertEquals(PlacementError.OVERLAP,
            placePanel(grid, initial, PanelId.AVATAR, height = 6).error)
        assertEquals(PlacementError.OVERLAP,
            placePanel(grid, initial, PanelId.AVATAR, width = 4).error)
    }
    @Test fun exactMinimumIsAcceptedButSmallerIsRefused() {
        assertNull(placePanel(grid, initial, PanelId.AVATAR, height = 3).error)
        for ((width, height) in listOf(2 to 4, 3 to 2, 0 to 0, -1 to 4)) {
            val result = placePanel(grid, initial, PanelId.AVATAR, width = width, height = height)
            assertEquals(PlacementError.TOO_SMALL, result.error)
            assertEquals(initial, result.panels)
        }
    }
    @Test fun resizedPanelCannotExceedGrid() {
        val single = listOf(initial.first())
        assertNull(placePanel(grid, single, PanelId.AVATAR, width = grid.columns, height = grid.rows).error)
        assertEquals(PlacementError.OUT_OF_BOUNDS,
            placePanel(grid, single, PanelId.AVATAR, width = grid.columns + 1).error)
        assertEquals(PlacementError.OUT_OF_BOUNDS,
            placePanel(grid, single, PanelId.AVATAR, height = grid.rows + 1).error)
    }
    @Test fun touchingEdgesIsNotAnOverlap() {
        val left = DashboardPanel(PanelId.AVATAR, 0, 0, 3, 3)
        val right = DashboardPanel(PanelId.SYSTEM, 3, 0, 3, 3)
        assertNull(placementError(grid, listOf(left), right))
        assertNull(placementError(grid, listOf(left), right.copy(x = 0, y = 3)))
    }
    @Test fun lockedPanelCannotMoveOrResize() {
        val locked = initial.map { it.copy(locked = true) }
        assertEquals(PlacementError.LOCKED, placePanel(grid, locked, PanelId.AVATAR, y = 1).error)
        assertEquals(PlacementError.LOCKED, placePanel(grid, locked, PanelId.AVATAR, height = 3).error)
        assertEquals(locked, placePanel(grid, locked, PanelId.AVATAR, y = 1).panels)
    }
    @Test fun unknownPanelDoesNotAlterLayout() {
        val result = placePanel(grid, initial.drop(1), PanelId.AVATAR, y = 1)
        assertEquals(PlacementError.UNKNOWN_PANEL, result.error)
        assertEquals(initial.drop(1), result.panels)
    }
    @Test fun extremeIntegerBoundsCannotWrapIntoGrid() {
        assertEquals(PlacementError.OUT_OF_BOUNDS,
            placePanel(grid, initial, PanelId.AVATAR, x = Int.MAX_VALUE, width = Int.MAX_VALUE).error)
        assertEquals(PlacementError.OUT_OF_BOUNDS,
            placePanel(grid, initial, PanelId.AVATAR, y = Int.MAX_VALUE, height = Int.MAX_VALUE).error)
    }
    @Test fun normalModeRejectsPlacementAndLockActions() {
        val state = HomeState()
        assertEquals(state, reduceHome(state, HomeAction.Place(profile, PanelId.AVATAR, 0, 1, 3, 4)))
        assertEquals(state, reduceHome(state, HomeAction.ToggleLock(profile, PanelId.AVATAR)))
    }
    @Test fun editModeCanBeEnteredAndExited() {
        val editing = reduceHome(HomeState(), HomeAction.ToggleEditing)
        assertTrue(editing.editing)
        assertFalse(reduceHome(editing, HomeAction.ToggleEditing).editing)
        assertFalse(reduceHome(editing, HomeAction.ExitEditing).editing)
    }
    @Test fun profilesKeepIndependentLayouts() {
        val state = reduceHome(HomeState(editing = true), HomeAction.Place(profile, PanelId.AVATAR, 0, 1, 3, 4))
        assertEquals(1, state.layouts.getValue(profile).first().y)
        assertEquals(initialPanels(DashboardProfile.WIDE), state.layouts.getValue(DashboardProfile.WIDE))
    }
    @Test fun lockingAndUnlockingAreEnforcedByReducer() {
        val locked = reduceHome(HomeState(editing = true), HomeAction.ToggleLock(profile, PanelId.AVATAR))
        val refused = reduceHome(locked, HomeAction.Place(profile, PanelId.AVATAR, 0, 1, 3, 4))
        assertEquals(PlacementError.LOCKED, refused.placementError)
        val unlocked = reduceHome(refused, HomeAction.ToggleLock(profile, PanelId.AVATAR))
        val moved = reduceHome(unlocked, HomeAction.Place(profile, PanelId.AVATAR, 0, 1, 3, 4))
        assertNull(moved.placementError)
        assertEquals(1, moved.layouts.getValue(profile).first().y)
    }
    @Test fun settingsCloseEditingButKeepSessionLayout() {
        val placed = reduceHome(HomeState(editing = true), HomeAction.Place(profile, PanelId.AVATAR, 0, 1, 3, 4))
        val settings = reduceHome(placed, HomeAction.OpenSettings)
        assertFalse(settings.editing)
        assertTrue(settings.settingsOpen)
        assertEquals(placed.layouts, reduceHome(settings, HomeAction.CloseSettings).layouts)
        assertEquals(settings, reduceHome(settings, HomeAction.ToggleEditing))
    }
    @Test fun rejectedPlacementReportsReasonAndNextValidPlacementClearsIt() {
        val refused = reduceHome(HomeState(editing = true), HomeAction.Place(profile, PanelId.AVATAR, -1, 0, 3, 4))
        assertEquals(PlacementError.OUT_OF_BOUNDS, refused.placementError)
        assertEquals(initial, refused.layouts.getValue(profile))
        assertNull(reduceHome(refused, HomeAction.Place(profile, PanelId.AVATAR, 0, 1, 3, 4)).placementError)
    }
    @Test fun deterministicMovementSequenceAlwaysKeepsValidLayout() {
        val random = Random(4004)
        for (profile in DashboardProfile.entries) {
            var panels = initialPanels(profile)
            repeat(1500) {
                val before = panels
                val result = placePanel(profile.grid, panels, PanelId.entries.random(random),
                    x = random.nextInt(-2, profile.grid.columns + 2),
                    y = random.nextInt(-2, profile.grid.rows + 2),
                    width = random.nextInt(1, 8), height = random.nextInt(1, 8))
                panels = result.panels
                if (result.error != null) assertEquals(before, panels)
                panels.forEach { assertNull(placementError(profile.grid, panels, it)) }
                assertEquals(PanelId.entries.toSet(), panels.map { it.id }.toSet())
            }
        }
    }
}
