filepath = 'app/src/main/java/com/theveloper/pixelplay/data/sharing/VybeSongShareLink.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

bad_str = """        append("

Listen on VYBE:
${build(song)}")"""

good_str = '        append("\\n\\nListen on VYBE:\\n${build(song)}")'

content = content.replace(bad_str, good_str)
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
