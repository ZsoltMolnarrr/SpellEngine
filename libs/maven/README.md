# Vendored Bundle API (1.20.1)

This directory is a file-based Maven repository holding **Bundle API `1.1.0+1.20.1`** (MIT, © TheRedBrain;
see `LICENSE`). It is built from the `1.20.1-modern` branch of the
[ZsoltMolnarrr/bundle-api](https://github.com/ZsoltMolnarrr/bundle-api) fork, which is offered upstream as
[TheRedBrain/bundle-api#6](https://github.com/TheRedBrain/bundle-api/pull/6).

Upstream has no 1.20.1 release on Modrinth yet, so the build scripts resolve
`com.github.TheRedBrain:bundle-api-<platform>:<bundle_api_version>` from here instead
(`repositories { maven { url = uri("${rootProject.projectDir}/libs/maven") } }`), which keeps CI builds
self-contained. Consumers that ship the library JiJ it and declare it as *embedded* on Modrinth/CurseForge.

**Exit path:** once upstream publishes a 1.20.1 build, switch the dependency back to
`maven.modrinth:bundle-api:<version>-<platform>`, drop the `VendoredBundleApi` repository entries, and delete
this directory.

**Rebuilding:** in the fork, `./gradlew build publishToMavenLocal` (JDK 21), then copy the `.jar`, `.pom` and
`.module` files from `~/.m2/repository/com/github/TheRedBrain/bundle-api-<platform>/<version>/` into the matching
path here.
