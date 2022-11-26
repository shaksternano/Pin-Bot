plugins {
    id("java")
}

group = "io.github.shaksternano"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:${project.property("jda_version")}")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:${project.property("apache_log4j_2_version")}")
    implementation("com.lmax:disruptor:${project.property("lmax_disruptor_version")}")
    implementation("org.mapdb:mapdb:${project.property("mabdb_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${project.property("junit_version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${project.property("junit_version")}")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
