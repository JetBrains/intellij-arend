import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.tasks.PatchPluginXmlTask

val projectArend = gradle.includedBuild("Arend")
group = "org.arend.lang"
version = "1.5.0"

plugins {
    idea
    kotlin("jvm") version "1.4.10"
    id("org.jetbrains.intellij") version "0.4.21"
    id("org.jetbrains.grammarkit") version "2020.2.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.arend:base")
    testImplementation(kotlin("reflect"))
    compileOnly(kotlin("stdlib"))
    implementation(kotlin("stdlib-jdk8").toString()) {
		exclude(module = "kotlin-stdlib")
    }
}

configure<JavaPluginConvention> {
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
    version = "2020.2"
    pluginName = "Arend"
    updateSinceUntilBuild = true
    instrumentCode = true
    setPlugins("yaml", "java", "IdeaVIM:0.57")
}

tasks.named<JavaExec>("runIde") {
    jvmArgs = listOf("-Xmx1g")
}

tasks.withType<PatchPluginXmlTask> {
    version(project.version)
    pluginId(project.group)
    changeNotes(file("src/main/html/change-notes.html").readText())
    pluginDescription(file("src/main/html/description.html").readText())
}

val generateArendLexer = task<GenerateLexer>("genArendLexer") {
    description = "Generates lexer"
    group = "build setup"
    source = "src/main/grammars/ArendLexer.flex"
    targetDir = "src/main/lexer/org/arend/lexer"
    targetClass = "ArendLexer"
    purgeOldFiles = true
}

val generateArendParser = task<GenerateParser>("genArendParser") {
    description = "Generates parser"
    group = "build setup"
    source = "src/main/grammars/ArendParser.bnf"
    targetRoot = "src/main/parser"
    pathToParser = "/org/arend/parser/ArendParser.java"
    pathToPsiRoot = "/org/arend/psi"
    purgeOldFiles = true
}

val generateArendDocLexer = task<GenerateLexer>("genArendDocLexer") {
    description = "Generates doc lexer"
    group = "build setup"
    source = "src/main/grammars/ArendDocLexer.flex"
    targetDir = "src/main/doc-lexer/org/arend/lexer"
    targetClass = "ArendDocLexer"
    purgeOldFiles = true
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "1.4"
        apiVersion = "1.4"
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
    dependsOn(generateArendLexer, generateArendParser, generateArendDocLexer)
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
    from(dir.resolve("lib/Prelude.ard"))
    into("src/main/resources/lib")
    dependsOn(projectArend.task(":cli:buildPrelude"))
}

tasks.withType<Wrapper> {
    gradleVersion = "6.5"
}

// Utils

fun prop(name: String): Any? = extra.properties[name]
