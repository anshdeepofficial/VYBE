git add .
git commit -m "Move Artist Preferences to Artist Settings and add GitHub issues link in About"
git push origin main
git tag v0.8.4
git push origin v0.8.4
gh release create v0.8.4 app\build\outputs\apk\release\VYBE-v0.8.4-arm64-v8a-release.apk app\build\outputs\apk\release\VYBE-v0.8.4-armeabi-v7a-release.apk --title "v0.8.4 - Artist Settings & GitHub Issues Link" --notes-file CHANGELOG.md
