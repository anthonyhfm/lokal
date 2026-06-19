plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.jetbrains.compose)
}

kotlin {
  jvm()
  macosArm64()
  linuxX64()
  mingwX64()

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.koin.core)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.mosaic.runtime)
        implementation(libs.mosaic.tty)
        implementation(libs.mosaic.tty.terminal)
      }
    }

    jvmMain {
      dependencies {
        implementation(libs.koog.agents)
      }
    }
  }
}
