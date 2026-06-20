import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.jetbrains.compose)
}

// ─── llama.cpp native build paths ─────────────────────────────────────────────

val llamaRootDir = rootProject.file("native/llama.cpp")
val llamaBuildDir = rootProject.file("native/build")

// ─── CMake build tasks ────────────────────────────────────────────────────────

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isMac = hostOs.contains("mac")
val isLinux = hostOs.contains("linux")

val cmakeExecutable = listOf(
    "/opt/homebrew/bin/cmake", 
    "/usr/local/bin/cmake", 
    "/usr/bin/cmake"
).find { file(it).exists() } ?: "cmake"

val configureLlamaCpp by tasks.registering(Exec::class) {
  group = "native"
  description = "Configures llama.cpp build with CMake"
  // Configuration cache doesn't support CMake process tasks
  notCompatibleWithConfigurationCache("Uses CMake process execution")
  onlyIf { llamaRootDir.exists() }

  doFirst { llamaBuildDir.mkdirs() }
  workingDir = llamaBuildDir

  val cmakeArgs = buildList {
    add(cmakeExecutable)
    add(llamaRootDir.absolutePath)
    add("-DCMAKE_BUILD_TYPE=Release")
    add("-DBUILD_SHARED_LIBS=OFF")         // Static libraries only
    add("-DLLAMA_BUILD_TESTS=OFF")         // Skip tests
    add("-DLLAMA_BUILD_EXAMPLES=OFF")      // Skip examples
    add("-DLLAMA_BUILD_SERVER=OFF")        // Skip server binary
    add("-DLLAMA_BUILD_TOOLS=OFF")         // Skip all CLI tools (avoids broken linker targets)
    if (isMac) {
      add("-DGGML_METAL=ON")               // Enable Metal GPU
      add("-DGGML_METAL_EMBED_LIBRARY=ON") // Embed .metallib — no extra file at runtime
    }
    if (isLinux) {
      add("-DGGML_METAL=OFF")
    }
  }
  commandLine(cmakeArgs)

  inputs.file(llamaRootDir.resolve("CMakeLists.txt"))
  outputs.dir(llamaBuildDir)
}

val buildLlamaCpp by tasks.registering(Exec::class) {
  group = "native"
  description = "Builds llama.cpp static libraries with CMake"
  // Configuration cache doesn't support CMake process tasks
  notCompatibleWithConfigurationCache("Uses CMake process execution")
  dependsOn(configureLlamaCpp)
  onlyIf { llamaRootDir.exists() }

  workingDir = llamaBuildDir
  commandLine(
    cmakeExecutable, "--build", ".",
    "--target", "llama",    // Only build libllama.a + its ggml deps — no executables
    "--config", "Release",
    "-j", Runtime.getRuntime().availableProcessors().toString()
  )

  // Incremental build: only re-run if sources changed
  inputs.dir(llamaRootDir.resolve("src"))
  inputs.dir(llamaRootDir.resolve("include"))
  inputs.dir(llamaRootDir.resolve("ggml/src"))
  inputs.dir(llamaRootDir.resolve("ggml/include"))
  outputs.dir(llamaBuildDir.resolve("src"))
  outputs.dir(llamaBuildDir.resolve("ggml/src"))
}

// ─── kotlin multiplatform ─────────────────────────────────────────────────────

kotlin {
  macosArm64()
  linuxX64()
  mingwX64()

  // Make cinterop bindings available in nativeMain across all native targets
  applyDefaultHierarchyTemplate()

  targets.withType<KotlinNativeTarget>().configureEach {
    compilations.getByName("main") {
      cinterops {
        val llama by creating {
          definitionFile.set(project.file("src/nativeInterop/cinterop/llama.def"))
          includeDirs.allHeaders(
            llamaRootDir.resolve("include"),
            llamaRootDir.resolve("ggml/include")
          )
        }
      }
    }

    binaries.all {
      val buildDir = llamaBuildDir
      // Link the static llama.cpp libraries
      linkerOpts(
        "-L${buildDir.resolve("src").absolutePath}",
        "-L${buildDir.resolve("ggml/src").absolutePath}",
        "-lllama",
        "-lggml",
        "-lggml-base",
        "-lggml-cpu",
      )
      if (isMac) {
        linkerOpts("-lggml-metal")
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.mosaic.runtime)
        implementation(libs.mosaic.tty)
        implementation(libs.mosaic.tty.terminal)
        implementation("ai.koog:agents-core:1.0.0")
      }
    }
  }
}

// ─── Make all cinterop tasks wait for the native build ────────────────────────

tasks.withType<CInteropProcess>().configureEach {
  dependsOn(buildLlamaCpp)
}
