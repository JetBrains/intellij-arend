plugins {
    java
    groovy
}

val dir = projectDir
        .parentFile
        .parentFile
        .resolve("Arend/buildSrc/src/main")

sourceSets {
    main {
        withConvention(GroovySourceSet::class) {
            groovy { srcDir(dir.resolve("groovy")) }
        }
        java { srcDir(dir.resolve("java")) }
    }
}