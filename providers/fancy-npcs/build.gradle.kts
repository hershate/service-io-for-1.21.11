repositories {
    maven("https://repo.fancyinnovations.com/releases")
}

dependencies {
    // FancyNpcs 2.10.0+ is compiled for Java 25 / MC 26.x and cannot be consumed by
    // the JDK 21 toolchain; pin to 2.9.2, the last release targeting MC 1.21.x (Java 17).
    compileOnly("de.oliver:FancyNpcs:2.9.2")
}