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
