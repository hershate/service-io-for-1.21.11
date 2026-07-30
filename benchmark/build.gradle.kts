plugins {
    id("application")
}

dependencies {
    implementation(rootProject)
    implementation(project(":plugin"))

    // The APIs the benchmark touches directly. They are compileOnly on the main modules;
    // here we promote them to the runtime classpath so the process can run off-platform.
    implementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("net.thenextlvl:vault-api:1.7.1")
    implementation("net.milkbowl.vault:VaultUnlockedAPI:2.15") {
        exclude("com.github.MilkBowl", "VaultAPI")
        exclude("org.jetbrains", "annotations")
    }
}

application {
    mainClass.set("net.thenextlvl.service.benchmark.BenchmarkRunner")
    applicationDefaultJvmArgs = listOf("-Xms256m", "-Xmx1g")
}
