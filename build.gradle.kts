plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = project.property("maven_group") as String
base.archivesName.set(project.property("archives_base_name") as String)
version = project.property("version") as String

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:${project.property("jda_version")}") {
        exclude(module = "opus-java")
    }
    implementation("org.mapdb:mapdb:${project.property("mabdb_version")}")
    implementation("com.google.code.gson:gson:${project.property("google_gson_version")}")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:${project.property("apache_log4j_2_version")}")
    implementation("com.lmax:disruptor:${project.property("lmax_disruptor_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${project.property("junit_version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${project.property("junit_version")}")
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "${project.group}.pinbot.Main"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
