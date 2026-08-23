package com.theveloper.pixelplay.utils

/**
 * Null-safe enum lookup helpers.
 *
 * Why this exists: `Enum.valueOf(name)` throws [IllegalArgumentException] when the
 * name does not match a constant, and it throws [NullPointerException] on a null
 * name. Both are trivially reachable in production:
 *
 *  - **R8 enum pruning.** In a minified release build the shrinker can delete enum
 *    constant fields that are only reachable via `values()`/`valueOf()`. We now keep
 *    them explicitly (see the ENUM CONSTANTS block in `proguard-rules.pro`), but a
 *    single regression to that file used to be enough to make `valueOf` throw in
 *    release builds only. This helper means such a regression degrades to a default
 *    instead of killing the process.
 *  - **Persisted values outliving a refactor.** Enum names written to DataStore,
 *    SharedPreferences, Room, or WorkManager input data survive app upgrades. If a
 *    constant is later renamed or removed, every stored value becomes unparseable.
 *
 * These are `inline` + `reified`, so `enumValues<T>()` is resolved by the compiler
 * and no runtime reflection is involved.
 */

/** Returns the constant of [T] whose name equals [name], or `null` if there is no match. Never throws. */
inline fun <reified T : Enum<T>> enumByNameOrNull(name: String?): T? {
    if (name.isNullOrEmpty()) return null
    return enumValues<T>().firstOrNull { it.name == name }
}

/** Returns the constant of [T] whose name equals [name], falling back to [default]. Never throws. */
inline fun <reified T : Enum<T>> enumByNameOrDefault(name: String?, default: T): T =
    enumByNameOrNull<T>(name) ?: default
