repositories {
    maven("https://jitpack.io")
}

dependencies {
    // DecentHolograms 2.10+ ships nms-v26 (Java 25 / MC 26.x) modules that cannot be
    // consumed by the JDK 21 toolchain; pin to 2.9.9, the release used for MC 1.21.x.
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.9.9")
}