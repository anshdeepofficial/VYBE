import re
with open('gradle.properties', 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'APP_VERSION_NAME=.*', 'APP_VERSION_NAME=0.8.1', content)
content = re.sub(r'APP_VERSION_CODE=.*', lambda m: f"APP_VERSION_CODE={int(m.group(0).split('=')[1])+1}", content)

with open('gradle.properties', 'w', encoding='utf-8') as f:
    f.write(content)
