package com.healthylauncher

object Tags {
    const val MODE_LABEL = "mode_label"
    const val TIMER = "timer"
    const val WARNING = "warning"
    const val ENTER_SOCIAL = "enter_social"
    const val END_SOCIAL = "end_social"
    const val END_CONFIRM = "end_confirm"
    const val END_CANCEL = "end_cancel"
    const val OPEN_SETTINGS = "open_settings"
    const val SET_HOME = "set_home"
    const val HOME_FILTER = "home_filter"
    const val PROGRESS = "challenge_progress"
    const val PROMPT = "challenge_prompt"
    const val REVEAL = "challenge_reveal"
    const val ANSWER = "challenge_answer"
    const val SUBMIT = "challenge_submit"
    const val HIDE = "challenge_hide"
    const val ERROR = "challenge_error"
    const val CANCEL = "challenge_cancel"
    const val BREATH = "breath_remaining"
    const val BACK = "back"
    const val PICKER_SEARCH = "picker_search"
    const val OPEN_APPS = "open_apps"
    const val APPEARANCE_GRAY = "appearance_text_gray"
    const val APPEARANCE_COLOR_TEXT = "appearance_text_color"
    const val APPEARANCE_MONO = "appearance_mono"
    const val APPEARANCE_ICONS = "appearance_color"
    const val CHALLENGE_DRILLS = "challenge_drills"
    const val CHALLENGE_MINDFUL = "challenge_mindful"
    const val CHALLENGE_JOURNAL = "challenge_journal"
    const val CHALLENGE_BREATHING = "challenge_breathing"
    const val BLOCK_SUSPEND = "block_suspend"
    const val BLOCK_ACCESSIBILITY = "block_accessibility"
    const val BLOCK_SHIZUKU = "block_shizuku"
    const val BLOCK_NOTIFICATIONS = "block_notifications"
    const val BLOCK_USAGE = "block_usage"

    fun appRow(packageName: String) = "app_" + safe(packageName)
    fun pickRow(packageName: String) = "pick_" + safe(packageName)
    fun icon(packageName: String, mono: Boolean): String {
        val prefix = if (mono) "icon_mono_" else "icon_color_"
        return prefix + safe(packageName)
    }

    private fun safe(packageName: String) = packageName.replace('.', '_')
}
