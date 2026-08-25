with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = lines[:3535] + lines[3565:]

with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
