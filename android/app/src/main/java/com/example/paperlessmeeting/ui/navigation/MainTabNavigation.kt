package com.example.paperlessmeeting.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController

const val MAIN_TABS_ROUTE = "main_tabs"

private const val MAIN_TAB_TARGET_PAGE_KEY = "main_tabs_target_page"

fun NavController.requestMainTabTransition(targetPage: Int) {
    getBackStackEntry(MAIN_TABS_ROUTE)
        .savedStateHandle[MAIN_TAB_TARGET_PAGE_KEY] = targetPage
}

fun SavedStateHandle.mainTabTransitionTarget(): Int? = get<Int>(MAIN_TAB_TARGET_PAGE_KEY)

fun SavedStateHandle.clearMainTabTransitionTarget() {
    remove<Int>(MAIN_TAB_TARGET_PAGE_KEY)
}
