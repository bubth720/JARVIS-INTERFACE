package com.jarvis.interfaceapp.domain

/** Integer cells, independent of pixels, density and orientation. */
data class Grid(val columns: Int, val rows: Int) {
    init { require(columns > 0 && rows > 0) }
}

enum class PanelId { AVATAR, SYSTEM, APPLICATIONS, COMMANDS }
enum class DashboardProfile(val grid: Grid) {
    COMPACT(Grid(6, 12)), WIDE(Grid(12, 10))
}

data class DashboardPanel(
    val id: PanelId,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val minWidth: Int = 3,
    val minHeight: Int = 3,
    val locked: Boolean = false,
)

enum class PlacementError { UNKNOWN_PANEL, LOCKED, TOO_SMALL, OUT_OF_BOUNDS, OVERLAP }

data class PlacementResult(val panels: List<DashboardPanel>, val error: PlacementError? = null)

fun initialPanels(profile: DashboardProfile): List<DashboardPanel> {
    val width = profile.grid.columns / 2
    val height = if (profile == DashboardProfile.COMPACT) 4 else 3
    return PanelId.entries.mapIndexed { index, id ->
        DashboardPanel(id, (index % 2) * width, (index / 2) * (height + 1), width, height)
    }
}

/** Edge contact is allowed. Long arithmetic prevents overflow for invalid inputs. */
fun placementError(grid: Grid, panels: List<DashboardPanel>, candidate: DashboardPanel): PlacementError? {
    if (candidate.minWidth < 1 || candidate.minHeight < 1 ||
        candidate.width < candidate.minWidth || candidate.height < candidate.minHeight) {
        return PlacementError.TOO_SMALL
    }
    if (candidate.x < 0 || candidate.y < 0 ||
        candidate.x.toLong() + candidate.width > grid.columns ||
        candidate.y.toLong() + candidate.height > grid.rows) return PlacementError.OUT_OF_BOUNDS
    if (panels.any { other ->
        other.id != candidate.id &&
            candidate.x.toLong() < other.x.toLong() + other.width &&
            other.x.toLong() < candidate.x.toLong() + candidate.width &&
            candidate.y.toLong() < other.y.toLong() + other.height &&
            other.y.toLong() < candidate.y.toLong() + candidate.height
    }) return PlacementError.OVERLAP
    return null
}

/** Atomic refusal: never shifts neighbours or partially applies a change. */
fun placePanel(
    grid: Grid,
    panels: List<DashboardPanel>,
    id: PanelId,
    x: Int? = null,
    y: Int? = null,
    width: Int? = null,
    height: Int? = null,
): PlacementResult {
    val old = panels.find { it.id == id } ?: return PlacementResult(panels, PlacementError.UNKNOWN_PANEL)
    if (old.locked) return PlacementResult(panels, PlacementError.LOCKED)
    val candidate = old.copy(x = x ?: old.x, y = y ?: old.y,
        width = width ?: old.width, height = height ?: old.height)
    val error = placementError(grid, panels, candidate)
    return if (error != null) PlacementResult(panels, error)
    else PlacementResult(panels.map { if (it.id == id) candidate else it })
}
