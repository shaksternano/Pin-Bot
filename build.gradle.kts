val jdaVersion: String by project
val mapdbVersion: String by project
val gsonVersion: String by project
val log4j2Version: String by project
val disruptorVersion: String by project
val junitVersion: String by project

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "io.github.shaksternano.pinbot"
base.archivesName.set("pin-bot")
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion") {
        exclude(module = "opus-java")
    }
    implementation("org.mapdb:mapdb:$mapdbVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2Version")
    implementation("com.lmax:disruptor:$disruptorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        minimize {
            exclude(dependency("org.apache.logging.log4j:.*:.*"))
            exclude(dependency("org.mapdb:.*:.*"))
        }
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "${project.group}.Main",
                )
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
