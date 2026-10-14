package com.kakao.taxi.util

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Stealth mode: hides/shows the app icon from the home screen and app drawer.
 *
 * Architecture:
 *   - MainActivity has NO launcher intent filter → disabling it would kill the app.
 *   - LauncherAlias IS the sole entry with MAIN + LAUNCHER → toggling it controls
 *     whether the icon appears in the drawer.
 *
 * Why icon stayed visible on One UI 8.5:
 *   setComponentEnabledSetting() signals the PackageManager but the Samsung launcher
 *   on One UI 8.5+ caches its app list and ignores ACTION_PACKAGE_CHANGED from the
 *   same package (security restriction added in One UI 8.0+).
 *
 * Fix: after toggling the component, kill the Samsung launcher process via
 *   ActivityManager.killBackgroundProcesses(). This requires KILL_BACKGROUND_PROCESSES
 *   permission (normal protection level — granted at install, no runtime prompt).
 *   When the launcher restarts it re-queries PackageManager → icon appears/disappears.
 *
 * Fallback: on non-Samsung launchers or if kill fails, also broadcast
 *   ACTION_PACKAGE_CHANGED so AOSP/Pixel launchers still refresh.
 */
object StealthHelper {

    private const val ALIAS_CLASS = "com.kakao.taxi.LauncherAlias"

    // Known Samsung launcher package names across One UI versions
    private val SAMSUNG_LAUNCHERS = listOf(
        "com.sec.android.app.launcher",   // One UI 8.0 and earlier
        "com.samsung.android.app.spage",   // One UI 8.5+ (Bixby home / integrated launcher)
    )

    fun setIconHidden(context: Context, hidden: Boolean) {
        val pm = context.packageManager
        val alias = ComponentName(context.packageName, ALIAS_CLASS)
        val newState = if (hidden)
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        // 1. Toggle the component
        try {
            pm.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP)
        } catch (_: Exception) {}

        // 2. Kill Samsung launcher so it restarts and re-queries PackageManager.
        //    KILL_BACKGROUND_PROCESSES is a normal permission — no runtime prompt.
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            SAMSUNG_LAUNCHERS.forEach { pkg ->
                am.killBackgroundProcesses(pkg)
            }
        } catch (_: Exception) {}

        // 3. Also send ACTION_PACKAGE_CHANGED for AOSP/Pixel/other launchers.
        try {
            context.sendBroadcast(
                Intent(Intent.ACTION_PACKAGE_CHANGED).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, arrayOf(ALIAS_CLASS))
                    putExtra(Intent.EXTRA_DONT_KILL_APP, true)
                }
            )
        } catch (_: Exception) {}
    }

    fun isIconHidden(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val alias = ComponentName(context.packageName, ALIAS_CLASS)
            pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) {
            false
        }
    }
}
