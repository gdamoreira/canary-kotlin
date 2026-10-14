# Canary Kotlin on Windows 11 + WSL2 (Ubuntu)

> Adapted from canary/docs/building/wsl-ubuntu.md for the Kotlin/JVM engine.
> Canary-Kotlin status: CMake/vcpkg replaced by JDK 25 and the Gradle wrapper.

## Supported OS

- Windows 11 + WSL2 with Ubuntu 24.04
- Windows 11 + WSL2 with Ubuntu 26.04

## 1. Install the required software

Inside the Ubuntu WSL distribution:

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

Alternatively, when the Ubuntu archive already provides an OpenJDK 25 package:

```bash
sudo apt update
sudo apt install -y openjdk-25-jdk
```

Verify and export `JAVA_HOME`:

```bash
java -version        # must report 25.x
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
echo "$JAVA_HOME"
```

Add the `export` line to `~/.bashrc` to keep it across sessions.

## 2. Download the source code

Keep the checkout inside the Linux filesystem for fast Gradle builds:

```bash
cd ~
git clone --depth 1 https://github.com/gdamoreira/canary-kotlin.git
cd canary-kotlin
```

Building from `/mnt/c/...` works but is much slower and can break file watching.

## 3. Folder structure

```text
~
└── canary-kotlin
```

No dependency bootstrap directory (such as vcpkg) is needed.

## 4. Build and run

```bash
./gradlew clean test          # full gate: zero failures, zero warnings
./gradlew run                 # H2 in-memory server on 7171/7172
```

For a distribution:

```bash
./gradlew installDist
./build/install/canary-kotlin/bin/canary-kotlin
```

## 5. Using it from Windows

When a Windows client must reach the WSL server, WSL2 usually forwards
`localhost` automatically. If it does not, start the server with `CANARY_IP`
set to an address the Windows host can reach:

```bash
CANARY_IP="$(hostname -I | awk '{print $1}')" ./gradlew run
```

and allow the WSL application through the Windows firewall if prompted.

> The first build downloads Gradle dependencies and compiles the whole tree; it
> takes longer than later incremental builds.
