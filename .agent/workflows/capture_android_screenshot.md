---
description: Capture Android Screenshot to doc/pic
---

1. Define the screenshot filename using the current timestamp.
   - Command: `Get-Date -Format "yyyyMMdd_HHmmss"` to generate a timestamp.
   - Set filename variable: `$filename = "screenshot_$(Get-Date -Format 'yyyyMMdd_HHmmss').png"`
2. Capture the screenshot on the Android device.
   - Command: `D:\AndroidSDK\platform-tools\adb.exe shell screencap -p /sdcard/temp_screenshot.png`
// turbo
3. Pull the screenshot to the local `doc/pic` directory.
   - Command: `D:\AndroidSDK\platform-tools\adb.exe pull /sdcard/temp_screenshot.png doc/pic/$filename`
// turbo
4. Clean up the temporary file on the device.
   - Command: `D:\AndroidSDK\platform-tools\adb.exe shell rm /sdcard/temp_screenshot.png`
5. Notify the user with the image reference.
   - The user requested to see the image in the dialog.
   - You MUST output the following markdown: `![Android Screenshot](doc/pic/$filename)`
