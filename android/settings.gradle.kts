pluginManagement { repositories { google { content { includeGroupByRegex("androidx.*"); includeGroupByRegex("com.android.*"); includeGroupByRegex("com.google.*") } }; mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google { content { includeGroupByRegex("androidx.*"); includeGroupByRegex("com.android.*"); includeGroupByRegex("com.google.*") } }; mavenCentral() } }
rootProject.name = "SloplexAndroid"
include(":app-mobile", ":app-tv", ":core:model", ":core:network", ":core:data", ":core:player", ":feature:session", ":ui:design", ":ui:mobile", ":ui:tv")
