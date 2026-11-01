package ru.granta.carlauncher.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledCarApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isPinnedInDock: Boolean
)

class AppDockRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("granta_dock_prefs", Context.MODE_PRIVATE)

    // Получение всех установленных приложений на магнитоле
    fun getAllLaunchableApps(): List<InstalledCarApp> {
        val pm: PackageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pinnedSet = getPinnedPackages()
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                InstalledCarApp(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm),
                    isPinnedInDock = pinnedSet.contains(pkg)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun getPinnedPackages(): Set<String> {
        return prefs.getStringSet(
            "pinned_dock_apps",
            setOf(
                "com.ts.MainUI",              // Радио
                "ru.yandex.music",            // Музыка
                "ru.yandex.yandexnavi",       // Карты / Навигатор
                "com.android.dialer",         // Телефон Bluetooth
                "org.prowl.torque",           // ОБД2
                "com.android.settings"        // Настройки
            )
        ) ?: emptySet()
    }

    // Добавление или удаление иконки существующего приложения в левый нижний док
    fun togglePinApp(packageName: String) {
        val current = getPinnedPackages().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet("pinned_dock_apps", current).apply()
    }

    fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}
