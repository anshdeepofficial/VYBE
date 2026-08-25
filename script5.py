import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

if 'val querySuggestions by' not in content:
    content = re.sub(
        r'var searchQuery by rememberSaveable \{ mutableStateOf\(playerViewModel\.searchQuery\) \}',
        r'var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }\n    val querySuggestions by playerViewModel.querySuggestions.collectAsStateWithLifecycle()',
        content
    )
    
    # In DockedSearchBar content:
    # searchInputFocusRequester), ... expanded = false,
    content = re.sub(
        r'expanded = false,\s*onExpandedChange = \{\},',
        r'expanded = querySuggestions.isNotEmpty() && searchQuery.isNotBlank(),\n                                onExpandedChange = {},',
        content
    )
    
    # And add content inside DockedSearchBar
    new_content = """
                            ) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
                                ) {
                                    androidx.compose.foundation.lazy.items(querySuggestions, key = { it }) { suggestion ->
                                        androidx.compose.foundation.layout.Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                searchQuery = suggestion
                                                playerViewModel.updateSearchQuery(suggestion)
                                                playerViewModel.onSearchQuerySubmitted(suggestion)
                                                keyboardController?.hide()
                                            }.padding(16.dp)
                                        ) {
                                            Icon(imageVector = androidx.compose.material.icons.Icons.Rounded.Search, contentDescription = null)
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(suggestion)
                                        }
                                    }
                                }
                            }"""
    
    content = re.sub(
        r'DockedSearchBar\(\s*inputField = \{.*?(leadingIcon = \{.*?\}.*?trailingIcon = \{.*?\}.*?)\}\s*\)\s*\{\s*\}',
        r'DockedSearchBar(\n                                inputField = { \g<1> }' + new_content,
        content,
        flags=re.DOTALL
    )
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Wired Search Suggestions in SearchScreen")
else:
    print("Already wired")
