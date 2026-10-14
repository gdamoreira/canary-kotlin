# Canary Kotlin on Debian

> Adapted from canary/docs/building/debian.md for the Kotlin/JVM engine.
> Canary-Kotlin status: CMake/vcpkg replaced by JDK 25 and the Gradle wrapper.

## Supported OS

- Debian 12 (bookworm) or newer

## 1. Install the required software

Git downloads the sources; a JDK 25 LTS runs Gradle and the server.

Option A — Eclipse Temurin from the Adoptium repository:

```bash
sudo apt update
sudo apt install -y wget gpg apt-transport-https git

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | \
  sudo gpg --dearmor --yes -o /etc/apt/keyrings/adoptium.gpg

echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" | \
  sudo tee /etc/apt/sources.list.d/adoptium.list

sudo apt update
sudo apt install -y temurin-25-jdk
```

Option B — SDKMAN:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk list java        # pick the current Temurin 25 build, then:
sdk install java <temurin-25-identifier>
```

Verify:

```bash
java -version        # must report 25.x
```

Set `JAVA_HOME` for the current shell (and in your shell profile for
persistence). The exact path depends on the installation method:

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
echo "$JAVA_HOME"
```

The Gradle build uses a toolchain declaration (`jvmToolchain(25)`), so a
correctly resolved JDK 25 is required even if another default JDK is installed.

## 2. Download the source code

```bash
git clone --depth 1 https://github.com/gdamoreira/canary-kotlin.git
cd canary-kotlin
```

## 3. Folder structure

```text
.
└── canary-kotlin
```

No dependency bootstrap directory (such as vcpkg) is needed.

## 4. Build and run

```bash
./gradlew clean test          # full gate: zero failures, zero warnings
./gradlew run                 # H2 in-memory server on 7171/7172
```

For a production-style run against PostgreSQL 16:

```bash
./gradlew installDist
CANARY_DB_HOST=127.0.0.1 CANARY_DB_NAME=canary CANARY_DB_USER=canary CANARY_DB_PASSWORD=secret \
  ./build/install/canary-kotlin/bin/canary-kotlin
```

> The first build downloads Gradle dependencies and compiles the whole tree; it
> takes longer than later incremental builds.
