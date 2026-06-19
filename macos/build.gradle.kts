import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  macosArm64()

  targets.withType<KotlinNativeTarget>().configureEach {
    binaries {
      executable {
        baseName = "lokal-macos"
        entryPoint = "lokal.macos.main"
        
        val llamaBuildDir = rootProject.file("native/build")
        linkerOpts(
          "-L${llamaBuildDir.resolve("src").absolutePath}",
          "-L${llamaBuildDir.resolve("ggml/src").absolutePath}",
          "-L${llamaBuildDir.resolve("ggml/src/ggml-metal").absolutePath}",
          "-L${llamaBuildDir.resolve("ggml/src/ggml-blas").absolutePath}",
          "-lllama",
          "-lggml",
          "-lggml-base",
          "-lggml-cpu",
          "-lggml-metal",
          "-lggml-blas",
          "-framework", "Accelerate",
          "-framework", "Metal",
          "-framework", "Foundation",
          "-framework", "MetalKit"
        )
      }
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.shared)
      }
    }
  }
}
