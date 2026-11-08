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
include(":mushaf")
include(":hifz")
include(":khatma")
include(":prayer")
include(":adhkar")
include(":duas")
include(":tasbeeh")
include(":asma")
