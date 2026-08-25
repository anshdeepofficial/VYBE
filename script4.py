import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\viewmodel\PlayerViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Check if querySuggestions already exists
if 'val querySuggestions' not in content:
    # We add it at the top where searchQuery is
    
    inject = """
    private val _querySuggestions = MutableStateFlow<List<String>>(emptyList())
    val querySuggestions: StateFlow<List<String>> = _querySuggestions.asStateFlow()
    private var suggestionJob: Job? = null
"""
    content = re.sub(r'(var searchQuery by mutableStateOf\(""\)\s*private set)', r'\1\n' + inject, content)

    
    load_method = """
    fun updateSearchQuery(query: String) {
        searchQuery = query
        suggestionJob?.cancel()
        if (query.isBlank()) {
            _querySuggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(300) // Debounce
            val region = "IN" // fallback
            val suggestions = runCatching { onlineMusicRepository.getSearchSuggestions(query, region) }.getOrNull() ?: emptyList()
            if (searchQuery == query) {
                _querySuggestions.value = suggestions
            }
        }
    }
"""
    content = re.sub(r'fun updateSearchQuery\(query: String\) \{\s*searchQuery = query\s*\}', load_method, content)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Added querySuggestions to PlayerViewModel")
else:
    print("querySuggestions already in PlayerViewModel")

