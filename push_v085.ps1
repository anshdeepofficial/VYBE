git add -A
git commit -m "Fix updates properly and finalize Artist Settings relocation (v0.8.5)"
git push origin main
git tag -a v0.8.5 -m "v0.8.5"
git push origin v0.8.5
gh release create v0.8.5 app/build/outputs/apk/release/*.apk --title "VYBE v0.8.5 - Update Fix & Artist Settings" --notes "### Fixed`n- Fixed the 'update is not newer' bug (bumped APP_VERSION_CODE to 31).`n- Modified update logic to accept version name changes even if code remains same.`n- Fixed crash in Artist Settings UI."
