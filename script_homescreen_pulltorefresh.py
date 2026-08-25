import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\HomeScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

box_scaffold = """    // Drawer state for sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isRefreshing by playerViewModel.isHomeRefreshing.collectAsStateWithLifecycle()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { playerViewModel.forceUpdateDailyMix() },
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold("""

content = content.replace("""    // Drawer state for sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(""", box_scaffold)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
