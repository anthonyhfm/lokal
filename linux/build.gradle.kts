import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  linuxX64()

  targets.withType<KotlinNativeTarget>().configureEach {
    binaries {
      executable {
        baseName = "lokal-linux"
        entryPoint = "lokal.linux.main"
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
