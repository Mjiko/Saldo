pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local repo: ML Kit text-recognition (bundled model) + its closure, fetched
        // via curl because the sandbox egress proxy blocks Gradle's Java TLS to
        // dl.google.com (PKIX). Central block, not project repos, so the
        // FAIL_ON_PROJECT_REPOS guard stays satisfied.
        maven { url = uri("file:///home/hatch/maven-local") }
    }
}
rootProject.name = "MoneyTracker"
include(":app")
