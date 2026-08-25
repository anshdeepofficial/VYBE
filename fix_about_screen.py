filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/AboutScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

new_item = """            item(key = "report_bug_feature") {
                SocialChip(
                    label = "Report bug or suggest features",
                    subtitle = "Open GitHub Issues to report problems or request features",
                    iconRes = R.drawable.github, // Or R.drawable.rounded_bug_report_24 if you have one, falling back to github for safety
                    contentDescription = "Report bug or suggest feature",
                    onClick = { openUrl(context, "https://github.com/anshdeepofficial/VYBE/issues") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

"""

if 'report_bug_feature' not in content:
    content = content.replace('            item(key = "changelog_link") {', new_item + '            item(key = "changelog_link") {')
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
