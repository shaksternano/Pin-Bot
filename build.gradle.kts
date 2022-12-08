import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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
    implementation("net.dv8tion:JDA:${project.property("jda_version")}")
    implementation("org.mapdb:mapdb:${project.property("mabdb_version")}")
    implementation("com.google.code.gson:gson:${project.property("google_gson_version")}")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:${project.property("apache_log4j_2_version")}")
    implementation("com.lmax:disruptor:${project.property("lmax_disruptor_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${project.property("junit_version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${project.property("junit_version")}")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("${project.property("archives_base_name")}-shadow")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "${project.group}.pinbot.Main"))
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
