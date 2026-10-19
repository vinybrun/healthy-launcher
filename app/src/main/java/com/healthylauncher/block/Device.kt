package com.healthylauncher.block

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

fun isDefaultHome(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 29) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}

fun currentImePackage(context: Context): String? {
    val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return id?.substringBefore('/')?.takeIf { it.isNotBlank() }
}

fun currentDialerPackage(context: Context): String? {
    return try {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    } catch (error: Throwable) {
        null
    }
}

fun goHome(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun launchPackage(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (error: Throwable) {
        false
    }
}

fun inputMethodPackages(context: Context): Set<String> {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptySet()
    return imm.enabledInputMethodList.map { it.packageName }.toSet()
}
