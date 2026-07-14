package com.applock.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applock.applocker.service.ProtectionWatchdogService
import com.applock.core.Graph
import com.applock.core.database.ProtectedAppEntity
import com.applock.core.database.SecurityEventEntity
import com.applock.core.database.SecurityEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isProtected: Boolean,
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = Graph.database.protectedAppDao()
    private val installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    val apps: StateFlow<List<InstalledApp>> =
        combine(installedApps, dao.observeAll()) { installed, protectedApps ->
            val protectedSet = protectedApps.filter { it.enabled }.map { it.packageName }.toSet()
            installed.map { (pkg, label) ->
                InstalledApp(pkg, label, pkg in protectedSet)
            }.sortedWith(compareByDescending<InstalledApp> { it.isProtected }.thenBy { it.label.lowercase() })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .asSequence()
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != context.packageName }
                    .map { it.packageName to pm.getApplicationLabel(it).toString() }
                    .toList()
            }
            installedApps.value = list
        }
    }

    fun setProtected(packageName: String, protect: Boolean) {
        viewModelScope.launch {
            if (protect) {
                dao.upsert(ProtectedAppEntity(packageName = packageName, enabled = true))
                // The watchdog stops itself when nothing is protected; protecting
                // an app must bring it back without waiting for the next launch.
                ProtectionWatchdogService.start(getApplication())
            } else {
                dao.delete(packageName)
            }
            Graph.database.securityEventDao().insert(
                SecurityEventEntity(
                    eventType = if (protect) SecurityEventType.APP_PROTECTED
                    else SecurityEventType.APP_UNPROTECTED,
                    packageName = packageName,
                )
            )
        }
    }
}
