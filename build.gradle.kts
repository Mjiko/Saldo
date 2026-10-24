// Buildscript classpaths (direct Maven coordinates) instead of the plugins {}
// block, so no Gradle Plugin Portal access is needed.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}
