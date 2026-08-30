package com.theveloper.pixelplay.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Home.route,
    Screen.Search.route,
    Screen.Library.route,
    Screen.SongRecognition.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Library.route -> 2
    Screen.SongRecognition.route -> 3
    else -> null
}
