import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.GrammarKitPluginExtension
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    kotlin("jvm") version "1.2.61"
    id("org.jetbrains.intellij") version "0.3.7"
    id("org.jetbrains.grammarkit") version "2018.1.7"
}

repositories {
    mavenCentral()
}

dependencies {
    compile(project(":vclang"))
    compileOnly(kotlin("stdlib-jdk8"))
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        languageVersion = "1.2"
        apiVersion = "1.2"
    }
    dependsOn("generateVcLexer", "generateVcParser")
}

java.sourceSets {
    getByName("main").java.srcDirs("src/gen")
}

idea {
    module {
        generatedSourceDirs.add(file("src/gen"))
        outputDir = file("$buildDir/classes/main")
        testOutputDir = file("$buildDir/classes/test")
    }
}

intellij {
    version = "2018.2"
    pluginName = "intellij-vclang"
    updateSinceUntilBuild = true
    instrumentCode = false
    setPlugins("yaml")
}

task<GenerateLexer>("generateVcLexer") {
    description = "Generates lexer"
    group = "Source"
    source = "src/main/grammars/VcLexer.flex"
    targetDir = "src/gen/org/vclang/lexer"
    targetClass = "VcLexer"
    purgeOldFiles = true
}

task<GenerateParser>("generateVcParser") {
    description = "Generates parser"
    group = "Source"
    source = "src/main/grammars/VcParser.bnf"
    targetRoot = "src/gen"
    pathToParser = "/org/vclang/parser/VcParser.java"
    pathToPsiRoot = "/org/vclang/psi"
    purgeOldFiles = true
}

tasks.withType<Test> {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
}

afterEvaluate {
    tasks.withType<Test> {
        testLogging {
            if (hasProp("showTestStatus") && prop("showTestStatus").toBoolean()) {
                events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}

task<Copy>("prelude") {
    from(project(":vclang").file("lib/Prelude.vc"))
    from(project(":vclang").file("${project(":vclang").buildDir}/classes/main/lib/Prelude.vcc"))
    into("src/main/resources/lib")
    dependsOn(project(":vclang").relativeProjectPath(":prelude"))
}

tasks.withType<Wrapper> {
    gradleVersion = "4.9"
}


// Utils

fun hasProp(name: String): Boolean = extra.has(name)

fun prop(name: String): String =
        extra.properties[name] as? String
                ?: error("Property `$name` is not defined in gradle.properties")
