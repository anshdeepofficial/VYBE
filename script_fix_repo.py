import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\data\repository\OnlineMusicRepository.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_func = """
    suspend fun getSearchSuggestions(query: String, region: String): List<String> {
        return youTubeEngine.getSearchSuggestions(query, region)
    }
"""

if 'suspend fun getSearchSuggestions(' not in content:
    # insert before the last closing brace
    content = content.rstrip()
    if content.endswith('}'):
        content = content[:-1] + new_func + '}'
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
