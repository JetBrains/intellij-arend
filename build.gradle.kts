import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

val projectArend = gradle.includedBuild("Arend")
group = "org.arend.lang"
version = "1.3.0"

plugins {
    idea
    kotlin("jvm") version "1.3.72"
    id("org.jetbrains.intellij") version "0.4.18"
    id("org.jetbrains.grammarkit") version "2020.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.arend:api")
    implementation("org.arend:base")
    compileOnly(kotlin("stdlib-jdk8"))
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        languageVersion = "1.3"
        apiVersion = "1.3"
    }
    dependsOn("generateArendLexer", "generateArendParser")
}

tasks["jar"].dependsOn(
        projectArend.task(":api:jar"),
        projectArend.task(":proto:jar"),
        projectArend.task(":base:jar")
)

sourceSets {
    main {
        java.srcDirs("src/gen")
    }
}

idea {
    module {
        generatedSourceDirs.add(file("src/gen"))
        outputDir = file("$buildDir/classes/main")
        testOutputDir = file("$buildDir/classes/test")
    }
}

intellij {
    version = "2020.1"
    pluginName = "Arend"
    updateSinceUntilBuild = true
    instrumentCode = true
    setPlugins("yaml", "java", "IdeaVIM:0.56", "org.vayafulano.relativeLineNumbers:1.0.1")
}

tasks.withType<PatchPluginXmlTask> {
    version(project.version)
    pluginId(project.group)
    changeNotes(file("src/main/html/change-notes.html").readText())
    pluginDescription(file("src/main/html/description.html").readText())
}

task<GenerateLexer>("generateArendLexer") {
    description = "Generates lexer"
    group = "Source"
    source = "src/main/grammars/ArendLexer.flex"
    targetDir = "src/gen/org/arend/lexer"
    targetClass = "ArendLexer"
    purgeOldFiles = true
}

task<GenerateParser>("generateArendParser") {
    description = "Generates parser"
    group = "Source"
    source = "src/main/grammars/ArendParser.bnf"
    targetRoot = "src/gen"
    pathToParser = "/org/arend/parser/ArendParser.java"
    pathToPsiRoot = "/org/arend/psi"
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
            if (prop("showTestStatus") == "true") {
                events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}

task<Copy>("prelude") {
    val dir = projectArend.projectDir
    from(dir.resolve("lib/Prelude.ard"), dir.resolve("lib/Prelude.arc"))
    into("src/main/resources/lib")
    dependsOn(projectArend.task(":cli:buildPrelude"))
}

tasks.withType<Wrapper> {
    gradleVersion = "6.3"
}

// Utils

fun prop(name: String): Any? = extra.properties[name]
