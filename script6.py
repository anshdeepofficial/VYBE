import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_content = """content = {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
    ) {
        androidx.compose.foundation.lazy.items(querySuggestions, key = { it }) { suggestion ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    searchQuery = suggestion
                    playerViewModel.updateSearchQuery(suggestion)
                    playerViewModel.onSearchQuerySubmitted(suggestion)
                    // keyboardController?.hide()
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(suggestion)
            }
        }
    }
}"""

content = re.sub(r'content = \{\}', new_content, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
