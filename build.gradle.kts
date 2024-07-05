import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.RunIdeBase
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

val projectArend = gradle.includedBuild("Arend")
group = "org.arend.lang"
version = "1.10.0"

plugins {
    idea
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.arend:base")
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    implementation("guru.nidi:graphviz-java:0.18.1")
}

java {
    // toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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

tasks {
    val test by getting(Test::class) {
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*Test.class")
    }
}

intellij {
    version.set("2024.1")
    pluginName.set("Arend")
    updateSinceUntilBuild.set(true)
    instrumentCode.set(true)
    plugins.set(listOf("org.jetbrains.plugins.yaml", "com.intellij.java", "IdeaVIM:2.12.0"))
}

tasks.named<JavaExec>("runIde") {
    jvmArgs = listOf("-Xmx2g")
}

tasks.withType<PatchPluginXmlTask>().configureEach {
    version.set(project.version.toString())
    pluginId.set(project.group.toString())
    changeNotes.set(file("src/main/html/change-notes.html").readText())
    pluginDescription.set(file("src/main/html/description.html").readText())
}

val generateArendLexer = tasks.register<GenerateLexerTask>("genArendLexer") {
    description = "Generates lexer"
    group = "build setup"
    sourceFile.set(file("src/main/grammars/ArendLexer.flex"))
    targetOutputDir.set(file("src/main/lexer/org/arend/lexer"))
    purgeOldFiles.set(true)
}

val generateArendParser = tasks.register<GenerateParserTask>("genArendParser") {
    description = "Generates parser"
    group = "build setup"
    sourceFile.set(file("src/main/grammars/ArendParser.bnf"))
    targetRootOutputDir.set(file("src/main/parser"))
    pathToParser.set("/org/arend/parser/ArendParser.java")
    pathToPsiRoot.set("/org/arend/psi")
    purgeOldFiles.set(true)
}

val generateArendDocLexer = tasks.register<GenerateLexerTask>("genArendDocLexer") {
    description = "Generates doc lexer"
    group = "build setup"
    sourceFile.set(file("src/main/grammars/ArendDocLexer.flex"))
    targetOutputDir.set(file("src/main/doc-lexer/org/arend/lexer"))
    purgeOldFiles.set(true)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        freeCompilerArgs.set(listOf("-Xjvm-default=all"))
    }
    dependsOn(generateArendLexer, generateArendParser, generateArendDocLexer)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2048m"
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
    gradleVersion = "8.5"
}

tasks.register<RunIdeBase>("generateArendLibHTML") {
    systemProperty("java.awt.headless", true)
    args = listOf("generateArendLibHtml") +
            (project.findProperty("pathToArendLib") as String? ?: "") +
            (project.findProperty("pathToArendLibInArendSite") as String? ?: "") +
            (project.findProperty("versionArendLib") as String? ?: "") +
            (project.findProperty("updateColorScheme") as String? ?: "")
}

// Utils

fun prop(name: String): Any? = extra.properties[name]
