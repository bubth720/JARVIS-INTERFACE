package com.jarvis.interfaceapp

import android.app.Application
import com.jarvis.interfaceapp.storage.DataStoreLayoutRepository
import com.jarvis.interfaceapp.ui.DashboardController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class JarvisApplication : Application() {
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val workspace by lazy {
        com.jarvis.interfaceapp.workspace.WorkspaceController(
            com.jarvis.interfaceapp.workspace.DataStoreWorkspaceRepository(
                { File(filesDir,"datastore/workspace-v1.json") },storeScope),commandScope)
    }
    val dashboard: DashboardController by lazy {
        DashboardController(DataStoreLayoutRepository(
            file = { File(filesDir, "datastore/dashboard.json") }, scope = storeScope,
        ), commandScope)
    }
}
