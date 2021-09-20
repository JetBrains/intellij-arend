import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

val projectArend = gradle.includedBuild("Arend")
group = "org.arend.lang"
version = "1.7.0"

plugins {
    idea
    kotlin("jvm") version "1.5.21"
    id("org.jetbrains.intellij") version "1.1.4"
    id("org.jetbrains.grammarkit") version "2021.1.3"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.arend:base")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21")
}

java {
    // toolchain.languageVersion.set(JavaLanguageVersion.of(11))
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks["jar"].dependsOn(
        projectArend.task(":api:jar"),
        projectArend.task(":proto:jar"),
        projectArend.task(":base:jar")
)

val generated = arrayOf("src/main/doc-lexer", "src/main/lexer", "src/main/parser")

sourceSets {
    main {
        java.srcDirs(*generated)
    }
}

idea {
    module {
        generatedSourceDirs.addAll(generated.map(::file))
        outputDir = file("$buildDir/classes/main")
        testOutputDir = file("$buildDir/classes/test")
    }
}

intellij {
    version.set("2021.2.2")
    pluginName.set("Arend")
    updateSinceUntilBuild.set(true)
    instrumentCode.set(true)
    plugins.set(listOf("yaml", "java", "IdeaVIM:0.69"))
}

tasks.named<JavaExec>("runIde") {
    jvmArgs = listOf("-Xmx1g")
}

tasks.withType<PatchPluginXmlTask>().configureEach {
    version.set(project.version.toString())
    pluginId.set(project.group.toString())
    changeNotes.set(file("src/main/html/change-notes.html").readText())
    pluginDescription.set(file("src/main/html/description.html").readText())
}

val generateArendLexer = tasks.register<GenerateLexer>("genArendLexer") {
    description = "Generates lexer"
    group = "build setup"
    source = "src/main/grammars/ArendLexer.flex"
    targetDir = "src/main/lexer/org/arend/lexer"
    targetClass = "ArendLexer"
    purgeOldFiles = true
}

val generateArendParser = tasks.register<GenerateParser>("genArendParser") {
    description = "Generates parser"
    group = "build setup"
    source = "src/main/grammars/ArendParser.bnf"
    targetRoot = "src/main/parser"
    pathToParser = "/org/arend/parser/ArendParser.java"
    pathToPsiRoot = "/org/arend/psi"
    purgeOldFiles = true
}

val generateArendDocLexer = tasks.register<GenerateLexer>("genArendDocLexer") {
    description = "Generates doc lexer"
    group = "build setup"
    source = "src/main/grammars/ArendDocLexer.flex"
    targetDir = "src/main/doc-lexer/org/arend/lexer"
    targetClass = "ArendDocLexer"
    purgeOldFiles = true
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "1.5"
        apiVersion = "1.5"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
    dependsOn(generateArendLexer, generateArendParser, generateArendDocLexer)
}

tasks.withType<Test>().configureEach {
    testLogging {
        if (prop("showTestStatus") == "true") {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.register<Copy>("prelude") {
    val dir = projectArend.projectDir
    from(dir.resolve("lib/Prelude.ard"))
    into("src/main/resources/lib")
    dependsOn(projectArend.task(":cli:buildPrelude"))
}

tasks.withType<Wrapper> {
    gradleVersion = "7.1"
}

// Utils

fun prop(name: String): Any? = extra.properties[name]
