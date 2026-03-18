package edu.singaporetech.inf2007quiz01.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import edu.singaporetech.inf2007quiz01.navigation.ContactRoute

/**
 * Activity-scoped ViewModel that keeps the backstack and CalBot list order in memory.
 * Survives rotation (ViewModel lifecycle) but resets on app relaunch,
 * which is exactly what the spec asks for.
 */
class CalBotListViewModel : ViewModel() {

    /** Nav backstack — starts at the contact list. */
    val backStack = mutableStateListOf<Any>(ContactRoute)

    /** Display order of CalBots, gets reordered when user goes back after computing. */
    val calBotOrder = mutableStateListOf<Int>().apply {
        addAll((1..30).toList())
    }

    /** Tracks the CalBot that had "=" pressed so we can promote it on back. */
    var lastComputedCalBotId: Int? by mutableStateOf(null)
        private set

    /** Per-CalBot mood strings, updated by the LLM after each "=" press. */
    val calBotMoods = mutableStateMapOf<Int, String>()

    fun updateMood(calBotId: Int, mood: String) {
        calBotMoods[calBotId] = mood
    }

    /** Bump a CalBot to the front of the list. */
    fun moveToTop(calBotId: Int) {
        calBotOrder.remove(calBotId)
        calBotOrder.add(0, calBotId)
    }

    fun navigateTo(route: Any) {
        backStack.add(route)
    }

    fun markComputed(calBotId: Int) {
        lastComputedCalBotId = calBotId
    }

    /**
     * Handles system back — if the user computed something ("=" was pressed),
     * promote that CalBot to the top of the list before popping.
     */
    fun navigateBack(): Boolean {
        if (backStack.size <= 1) return false

        val lastComputed = lastComputedCalBotId
        if (lastComputed != null) {
            moveToTop(lastComputed)
            lastComputedCalBotId = null
        }

        backStack.removeLastOrNull()
        return true
    }
}
