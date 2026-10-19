package com.healthylauncher.domain

data class BlockSnapshot(
    val mode: Mode,
    val allowlist: Set<String>,
    val launchable: Set<String>,
    val protectedPackages: Set<String>,
    val selfPackage: String,
)

data class SuspendDiff(
    val suspend: Set<String>,
    val unsuspend: Set<String>,
)

fun visibleOnHome(packageName: String, snap: BlockSnapshot): Boolean {
    if (packageName == snap.selfPackage) return false
    if (packageName !in snap.launchable) return false
    return when (snap.mode) {
        Mode.SOCIAL -> true
        Mode.NORMAL -> packageName in snap.allowlist
    }
}

fun launchBlocked(packageName: String, snap: BlockSnapshot): Boolean {
    if (snap.mode == Mode.SOCIAL) return false
    if (packageName == snap.selfPackage) return false
    if (packageName in snap.protectedPackages) return false
    if (packageName !in snap.launchable) return false
    return packageName !in snap.allowlist
}

/** Launchable apps we are willing to suspend or unsuspend. */
fun managedPackages(snap: BlockSnapshot): Set<String> {
    return snap.launchable
        .filter { it != snap.selfPackage && it !in snap.protectedPackages }
        .toSet()
}

fun packagesToSuspend(snap: BlockSnapshot): Set<String> {
    if (snap.mode == Mode.SOCIAL) return emptySet()
    return managedPackages(snap).filter { launchBlocked(it, snap) }.toSet()
}

/**
 * Session end closes every managed app, not only the ones opened during the session.
 * Packages opened by other means are included when they are not protected.
 */
fun packagesToClose(snap: BlockSnapshot, opened: Set<String>): Set<String> {
    val extras = opened.filter { pkg ->
        pkg.isNotBlank() && pkg != snap.selfPackage && pkg !in snap.protectedPackages
    }
    return managedPackages(snap) + extras
}

fun suspendDiff(currently: Set<String>, desired: Set<String>): SuspendDiff {
    return SuspendDiff(
        suspend = desired - currently,
        unsuspend = currently - desired,
    )
}

fun filterCloseable(packages: Set<String>, inCall: Boolean, dialer: String?): Set<String> {
    if (!inCall) return packages
    val keep = setOfNotNull(
        dialer?.takeIf { it.isNotBlank() },
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.android.dialer",
    )
    return packages - keep
}

object CriticalPackages {
    val ALWAYS: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.providers.settings",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.shell",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.vpndialogs",
        "com.android.intentresolver",
        "com.miui.securitycenter",
        "com.miui.powerkeeper",
    )

    fun resolve(self: String, ime: String?, dialer: String?): Set<String> {
        return ALWAYS + self + listOfNotNull(ime, dialer).filter { it.isNotBlank() }
    }
}

fun shouldCancelNotification(packageName: String, snap: BlockSnapshot, category: String?): Boolean {
    if (category == "call" || category == "alarm") return false
    return launchBlocked(packageName, snap)
}

fun lastResumed(events: List<Pair<Int, String>>, resumedType: Int): String? {
    return events.lastOrNull { it.first == resumedType }?.second
}
