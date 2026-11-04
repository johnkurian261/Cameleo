# CAMELEO Workspace Rules & Execution Policy

## 1. Scope and Core Directives
This configuration defines the operational and permission guidelines for the CAMELEO workspace.
Antigravity must adhere strictly to the principle of least privilege, running standard development workflows smoothly while always asking for explicit user approval before any destructive or high-risk actions.

---

## 2. Routine Non-Destructive Operations (Execute Automatically)
Execute the following operations directly whenever supported by the current Antigravity sandbox and approval settings:

1. **File Reading & Codebase Search**:
   - Reading source code, layouts, shaders, assets, configurations, and build files.
   - Grepping, searching, and inspecting project directories.
2. **Non-Destructive Git Inspection**:
   - `git status`, `git diff`, `git log`, `git show`, `git branch`.
   - Staging specific modified files and creating descriptive checkpoint commits.
3. **Gradle Build and Test Execution**:
   - Compiling projects (`.\gradlew.bat assembleDebug`, `.\gradlew.bat compileDebugKotlin`).
   - Running unit tests (`.\gradlew.bat test`).
   - Running lint and static analysis checks.
4. **ADB Device Diagnostics and Inspection**:
   - Checking connected devices (`adb devices`).
   - Reading logcat diagnostic streams (`adb logcat`).
   - Inspecting hardware stats (`adb shell dumpsys gfxinfo`, `adb shell dumpsys meminfo`).
   - Querying device capabilities (`adb shell getprop`, `adb shell pm list features`).
   - Non-destructive UI inspection and benchmarking (`adb shell screencap`, `adb shell uiautomator dump`).
5. **Safe APK Deployment**:
   - Installing debug builds after verifying the destination APK path (`adb install -r app\build\outputs\apk\debug\app-debug.apk`).
   - Launching the application main activity (`adb shell am start -n com.craftcam.app/.MainActivity`).

---

## 3. Operations Requiring Explicit Approval (Always Ask User First)
Never execute the following operations without explicit confirmation from the user:

1. **File & Folder Deletion**:
   - Deleting or wiping code, resources, assets, or project directories (`Remove-Item -Recurse`, `rm -rf`, `git rm`).
2. **Destructive Git Operations**:
   - Hard resets (`git reset --hard`), force pushes (`git push --force`), branch deletions (`git branch -D`), rebasing with dropped commits, or untracked file purging (`git clean -fd`).
3. **Unknown or External Scripts**:
   - Running unverified third-party shell scripts, remote download scripts (e.g. `Invoke-WebRequest ... | iex`, `curl | sh`), or unreviewed `.bat`/`.ps1` executables.
4. **System-Wide Installations & Changes**:
   - Global package manager commands modifying machine state, OS environment variables, system drivers, or system directories.
5. **Security, Permission, & Sandbox Changes**:
   - Defeating, bypassing, or altering Antigravity security controls or global configurations (`~/.gemini/config/config.json`).
6. **External Account & Cloud Access**:
   - Modifying authentication providers, signing into third-party services, uploading local project assets to external cloud endpoints.

---

## 4. Command Execution Discipline
- **Try Sandboxed First**: Attempt commands in standard sandbox mode first. Only use elevated/bypass sandbox when genuinely interacting with hardware daemons (e.g. ADB) or local network/loopback daemons (e.g. Gradle daemon socket) where the sandbox blocks.
- **Prefix-Matchable Command Shapes**:
  - Invoke binaries directly (`git`, `adb`, `.\gradlew.bat`) rather than wrapping them in unnecessary subshells or complex compound expressions.
  - Keep commands clean and idempotent to enable Antigravity's generalized prefix-matching approval rules.
- **Verification Integrity**: Always verify artifact and build file paths before triggering deployment commands.
