pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppLock"
include(":app")

// The Kotlin plugin keeps its project data, such as the compiler session files, in .gradle/ (ignored by git) and not
// in a .kotlin/ folder in the project root. The path is absolute, because the plugin resolves a relative path against
// the working directory of the Gradle daemon.
gradle.beforeProject {
    extensions.extraProperties["kotlin.project.persistent.dir"] = rootDir.resolve(".gradle/kotlin").absolutePath
}
