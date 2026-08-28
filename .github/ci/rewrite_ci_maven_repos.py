#!/usr/bin/env python3
"""Rewrite frozen Android settings.gradle.kts on GitHub runners only.

The committed file keeps Aliyun first for China local builds. GitHub-hosted
runners get 502 from maven.aliyun.com, so CI checkout is switched to
Google / Maven Central / JitPack before Gradle runs.
"""

from pathlib import Path

SETTINGS = Path("settings.gradle.kts")
SETTINGS.write_text(
    """\
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("${rootDir}/local-maven") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BJTUselfService"
include(":app")
""",
    encoding="utf-8",
)
print(f"rewrote {SETTINGS.resolve()} for GitHub Maven Central")
