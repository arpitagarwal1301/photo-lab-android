package dev.spikeai.spike.appfunctions

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

const val EXECUTE_APP_FUNCTIONS = "android.permission.EXECUTE_APP_FUNCTIONS"

/** Everything Spike B needs to answer: can a normal app call other apps' AppFunctions? */
data class AccessReport(
    val device: String,
    val android: String,
    val sdkInt: Int,
    val permissionDefined: Boolean,
    val protection: String,
    val protectionFlagsHex: String,
    val permissionGranted: Boolean,
    val assistantRoleAvailable: Boolean,
    val assistantRoleHeld: Boolean,
    val appFunctionManagerAvailable: Boolean,
) {
    fun toJson(): String = """
        {
          "device": "$device",
          "android": "$android",
          "sdk_int": $sdkInt,
          "permission_defined": $permissionDefined,
          "protection": "$protection",
          "protection_flags_hex": "$protectionFlagsHex",
          "permission_granted": $permissionGranted,
          "assistant_role_available": $assistantRoleAvailable,
          "assistant_role_held": $assistantRoleHeld,
          "app_function_manager_available": $appFunctionManagerAvailable
        }
    """.trimIndent()
}

fun buildAccessReport(context: Context): AccessReport {
    val pm = context.packageManager
    val info: PermissionInfo? = try {
        pm.getPermissionInfo(EXECUTE_APP_FUNCTIONS, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
    val roleManager = context.getSystemService(RoleManager::class.java)

    return AccessReport(
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        permissionDefined = info != null,
        protection = info?.let { protectionName(it.protection) } ?: "n/a",
        protectionFlagsHex = info?.let { "0x" + Integer.toHexString(it.protectionFlags) } ?: "n/a",
        permissionGranted = context.checkSelfPermission(EXECUTE_APP_FUNCTIONS) ==
            PackageManager.PERMISSION_GRANTED,
        assistantRoleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true,
        assistantRoleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true,
        appFunctionManagerAvailable = appFunctionManagerAvailable(context),
    )
}

private fun protectionName(protection: Int): String = when (protection) {
    PermissionInfo.PROTECTION_NORMAL -> "normal"
    PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
    PermissionInfo.PROTECTION_SIGNATURE -> "signature"
    PermissionInfo.PROTECTION_INTERNAL -> "internal"
    else -> "unknown($protection)"
}

private fun appFunctionManagerAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    return context.getSystemService(android.app.appfunctions.AppFunctionManager::class.java) != null
}
