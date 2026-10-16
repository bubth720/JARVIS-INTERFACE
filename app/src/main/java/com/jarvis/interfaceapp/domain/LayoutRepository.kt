package com.jarvis.interfaceapp.domain

typealias Layouts = Map<DashboardProfile, List<DashboardPanel>>
fun defaultLayouts(): Layouts = DashboardProfile.entries.associateWith(::initialPanels)

enum class LayoutReadIssue { CORRUPT, INCOMPATIBLE }
data class LoadedLayouts(val layouts: Layouts, val exists: Boolean, val issue: LayoutReadIssue? = null)

/** A successful return acknowledges a completed durable transaction, not an enqueue. */
interface LayoutRepository {
    suspend fun load(): LoadedLayouts
    suspend fun save(layouts: Layouts)
    suspend fun reset(): Layouts
}
