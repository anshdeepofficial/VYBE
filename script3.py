import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\components\TimerOptionsBottomSheet.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'Spacer\(modifier = Modifier\.height\(4\.dp\)\)\s*Box\(\s*modifier = Modifier\s*\.align\(Alignment\.CenterHorizontally\)\s*\.fillMaxWidth\(\)\s*\.padding\(horizontal = 0\.dp\)\s*\.background\(\s*color = MaterialTheme\.colorScheme\.surfaceContainerHigh,\s*shape = RoundedCornerShape\(\s*bottomEnd = 18\.dp,\s*bottomStart = 18\.dp,\s*topEnd = 6\.dp,\s*topStart = 6\.dp\s*\)\s*\)\s*\)\s*\{\s*Column\(modifier = Modifier\.fillMaxWidth\(\)\) \{.*?Spacer\(modifier = Modifier\.height\(8\.dp\)\)'

content = re.sub(pattern, 'Spacer(modifier = Modifier.height(8.dp))', content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

