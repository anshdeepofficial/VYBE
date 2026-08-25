filepath = 'gradle.properties'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('APP_VERSION_CODE=29', 'APP_VERSION_CODE=31')
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
