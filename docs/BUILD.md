# Build

A compilação oficial da v0.1 usa GitHub Actions.

Workflow: `.github/workflows/android.yml`

Etapas:
1. Java 17
2. Android SDK 35 / Build Tools 35.0.0
3. Gradle 8.9
4. `gradle :app:assembleDebug`
5. upload do artifact `ViniVideoAI-debug-apk`
