import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  mingwX64()

  targets.withType<KotlinNativeTarget>().configureEach {
    binaries {
      executable {
        baseName = "lokal-windows"
        entryPoint = "lokal.windows.main"
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
