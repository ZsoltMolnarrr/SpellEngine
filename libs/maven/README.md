# Vendored Bundle API (1.20.1)

This directory is a file-based Maven repository holding **Bundle API `1.3.0+1.20.1`** (MIT, © TheRedBrain;
see `LICENSE`). It is built from the `1.20.1-modern` branch of the
[ZsoltMolnarrr/bundle-api](https://github.com/ZsoltMolnarrr/bundle-api) fork, which is offered upstream as
[TheRedBrain/bundle-api#6](https://github.com/TheRedBrain/bundle-api/pull/6). The code is the fork's 1.1.0;
only the version number is raised.

**Why 1.3.0:** the upstream `1.2.0+1.20.1` release on Modrinth is broken in production on both loaders (its jars
are published in Yarn/dev names, e.g. `CustomBundleItem extends net/minecraft/item/Item`, so it crashes with
`NoClassDefFoundError`). Forge keeps the newest version of a mod id, so an embedded build above 1.2.0 wins over a
standalone or JiJ'd 1.2.0. For the same reason Bundle API is **not** declared as a dependency on
Modrinth/CurseForge (the `embeds` entries are commented out in the publish config): a launcher-installed
standalone copy would override the embedded one on Fabric, where mods-folder jars always win.

The build scripts resolve `com.github.TheRedBrain:bundle-api-<platform>:<bundle_api_version>` from here
(`repositories { maven { url = uri("${rootProject.projectDir}/libs/maven") } }`), which keeps CI builds
self-contained. Consumers that ship the library JiJ it.

**Exit path:** once upstream publishes a correctly remapped 1.20.1 build (check that the jars reference production
names: `net/minecraft/class_…` on Fabric, `net/minecraft/world/…` + `m_…_` on Forge), switch the dependency back to
`maven.modrinth:bundle-api:<version>-<platform>`, drop the `VendoredBundleApi` repository entries, restore the
`embeds` entries, and delete this directory.

**Rebuilding:** in the fork, `./gradlew build publishToMavenLocal -Pmod_version=1.3.0` (JDK 21), then copy the
`.jar`, `.pom` and `.module` files from `~/.m2/repository/com/github/TheRedBrain/bundle-api-<platform>/<version>/`
into the matching path here.
