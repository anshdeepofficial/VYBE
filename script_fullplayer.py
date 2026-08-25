import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\components\player\FullPlayerContent.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add timerStringProvider to BottomToggleRow
bottom_row_args_old = """    isTimerActiveProvider: () -> Boolean = { false },
    onSleepTimerToggle: () -> Unit = {}
) {"""
bottom_row_args_new = """    isTimerActiveProvider: () -> Boolean = { false },
    timerStringProvider: () -> String? = { null },
    onSleepTimerToggle: () -> Unit = {}
) {"""
content = content.replace(bottom_row_args_old, bottom_row_args_new)

# Update BottomToggleRow call in FullPlayerContent
call_old = """                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle,
                isTimerActiveProvider = { isSleepTimerActive },
                onSleepTimerToggle = onSleepTimerClick
            )"""
call_new = """                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle,
                isTimerActiveProvider = { isSleepTimerActive },
                timerStringProvider = { playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle(null).value },
                onSleepTimerToggle = onSleepTimerClick
            )"""
content = content.replace(call_old, call_new)

# Update ToggleSegmentButton inside BottomToggleRow
timer_button_old = """            ToggleSegmentButton(
                modifier = commonModifier,
                active = isTimerActiveProvider(),
                activeColor = LocalMaterialTheme.current.primaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onPrimaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onSleepTimerToggle,
                iconId = R.drawable.rounded_timer_24,
                contentDesc = "Timer"
            )"""
timer_button_new = """            val timerString = timerStringProvider()
            if (isTimerActiveProvider() && timerString != null) {
                ToggleSegmentButtonText(
                    modifier = commonModifier,
                    active = true,
                    activeColor = LocalMaterialTheme.current.primaryFixed,
                    activeCornerRadius = rowCorners,
                    activeContentColor = LocalMaterialTheme.current.onPrimaryFixed,
                    inactiveColor = inactiveBg,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onSleepTimerToggle,
                    text = timerString,
                    contentDesc = "Timer Active"
                )
            } else {
                ToggleSegmentButton(
                    modifier = commonModifier,
                    active = isTimerActiveProvider(),
                    activeColor = LocalMaterialTheme.current.primaryFixed,
                    activeCornerRadius = rowCorners,
                    activeContentColor = LocalMaterialTheme.current.onPrimaryFixed,
                    inactiveColor = inactiveBg,
                    inactiveContentColor = inactiveContentColor,
                    onClick = onSleepTimerToggle,
                    iconId = R.drawable.rounded_timer_24,
                    contentDesc = "Timer"
                )
            }"""
content = content.replace(timer_button_old, timer_button_new)

# Ensure ToggleSegmentButtonText is imported
if 'import com.theveloper.pixelplay.presentation.components.ToggleSegmentButtonText' not in content:
    content = content.replace('import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton', 'import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton\nimport com.theveloper.pixelplay.presentation.components.ToggleSegmentButtonText')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
