# Canary Kotlin on Windows (Gradle)

> Adapted from canary/docs/building/windows-(cmake).md and
> canary/docs/building/windows-(visual-studio-solution).md for the Kotlin/JVM
> engine.
> Canary-Kotlin status: the two upstream Windows workflows (CMake CLI and Visual
> Studio solution) merge into one Gradle/IDE guide. The Visual Studio + vcpkg
> toolchain is not used.

## Supported OS

- Windows 10/11 (64-bit)

## 1. Install the required software

Download and install:

- [Git for Windows](https://git-scm.com/download/win)
- **JDK 25 LTS** — Eclipse Temurin 25 MSI from
  [Adoptium](https://adoptium.net/temurin/releases/?version=25), or any JDK 25
  distribution
- Optional: [IntelliJ IDEA](https://www.jetbrains.com/idea/) (recommended IDE)

During the Temurin installer, enable **Set JAVA_HOME variable** and **Add to
PATH**. If the installer did not set it, configure it manually:

```powershell
[System.Environment]::SetEnvironmentVariable('JAVA_HOME','C:\Program Files\Eclipse Adoptium\jdk-25.x.x-hotspot', [System.EnvironmentVariableTarget]::Machine)
```

Verify in a new terminal:

```powershell
java -version        # must report 25.x
echo $env:JAVA_HOME
```

There is no Visual Studio, vcpkg, CMake or Ninja requirement.

## 2. Download the source code

```powershell
cd C:\
git clone https://github.com/gdamoreira/canary-kotlin.git
cd canary-kotlin
```

## 3. Build and run with the Gradle wrapper

```powershell
.\gradlew.bat clean test          # full gate: zero failures, zero warnings
.\gradlew.bat run                 # H2 in-memory server on 7171/7172
```

For the runnable distribution:

```powershell
.\gradlew.bat installDist
.\build\install\canary-kotlin\bin\canary-kotlin.bat
```

## 4. Build and run from an IDE (IntelliJ IDEA)

- Open the repository folder as a Gradle project.
- Wait for the Gradle sync to finish; IntelliJ uses the same wrapper and JDK 25
  toolchain as the command line.
- Run the application configuration for `org.opentibia.canary.MainKt`, or use
  the Gradle `run` task from the Gradle tool window.
- Run `test` from the Gradle tool window or with `.\gradlew.bat test`.

## 5. Docker alternative

If you prefer a production-like stack without installing a JDK:

```powershell
docker compose up -d --build
```

See [../docker/quickstart-for-beginners.md](../docker/quickstart-for-beginners.md).
