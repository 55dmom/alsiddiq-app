pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AlSiddiq"
include(":app")
// His own side buttons («الأدوات»), as independent library modules.
include(":tasbeeh")
include(":asma")
