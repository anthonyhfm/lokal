plugins {
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.jetbrains.compose) apply false
}

allprojects {
  group = "dev.lokal"
  version = "0.1.0"
}

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()

val hostLinkTask = when {
  hostOs.contains("mac") && (hostArch == "aarch64" || hostArch == "arm64") -> ":macos:linkDebugExecutableMacosArm64"
  hostOs.contains("linux") && hostArch.contains("64") -> ":linux:linkDebugExecutableLinuxX64"
  hostOs.contains("win") && hostArch.contains("64") -> ":windows:linkDebugExecutableMingwX64"
  hostOs.contains("mac") -> error("This project currently only defines a macOS ARM64 native run target.")
  else -> error("Unsupported host platform: os=$hostOs arch=$hostArch")
}

val hostBinaryPath = when {
  hostOs.contains("mac") && (hostArch == "aarch64" || hostArch == "arm64") -> "macos/build/bin/macosArm64/debugExecutable/lokal-macos.kexe"
  hostOs.contains("linux") && hostArch.contains("64") -> "linux/build/bin/linuxX64/debugExecutable/lokal-linux.kexe"
  hostOs.contains("win") && hostArch.contains("64") -> "windows/build/bin/mingwX64/debugExecutable/lokal-windows.exe"
  hostOs.contains("mac") -> error("This project currently only defines a macOS ARM64 native run target.")
  else -> error("Unsupported host platform: os=$hostOs arch=$hostArch")
}

tasks.register("buildHostExecutable") {
  group = "application"
  description = "Builds the native executable for the active host platform."
  dependsOn(hostLinkTask)
}

tasks.register("run") {
  group = "application"
  description = "Explains how to run the Mosaic TUI on the active host platform."
  dependsOn("buildHostExecutable")
  doLast {
    error(
      """
      Mosaic requires a real TTY and cannot run correctly through Gradle or IntelliJ.

      Built host executable:
        $hostBinaryPath

      Run it directly from Ghostty or another terminal:
        ./lokal
      """.trimIndent()
    )
  }
}
