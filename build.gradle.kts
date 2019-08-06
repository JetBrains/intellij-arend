import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    kotlin("jvm") version "1.3.41"
    id("org.jetbrains.intellij") version "0.4.9"
    id("org.jetbrains.grammarkit") version "2019.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":Arend")) {
        isTransitive = false
    }
    compileOnly(kotlin("stdlib-jdk8"))

    // Transitive dependencies of :Arend
    implementation("com.google.protobuf:protobuf-java:3.7.1")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        languageVersion = "1.3"
        apiVersion = "1.3"
    }
    dependsOn("generateArendLexer", "generateArendParser")
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
    version = "2019.2"
    pluginName = "Arend"
    updateSinceUntilBuild = true
    instrumentCode = false
    setPlugins("yaml", "java")
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
            if (hasProp("showTestStatus") && prop("showTestStatus").toBoolean()) {
                events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}

task<Copy>("prelude") {
    from(project(":Arend").file("lib/Prelude.ard"))
    from(project(":Arend").file("${project(":Arend").buildDir}/classes/java/main/lib/Prelude.arc"))
    into("src/main/resources/lib")
    dependsOn(":Arend:prelude")
}

tasks.withType<Wrapper> {
    gradleVersion = "4.9"
}


// Utils

fun hasProp(name: String): Boolean = extra.has(name)

fun prop(name: String): String =
        extra.properties[name] as? String
                ?: error("Property `$name` is not defined in gradle.properties")
