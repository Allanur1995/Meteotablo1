import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm") version "2.2.10"
  application
}

group = "com.aistudio.meteotablo"
version = "1.0.0"

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
  sourceSets {
    main {
      kotlin {
        srcDir("src/main/kotlin")
        srcDir("../app/src/main/java/com/example/model")
        srcDir("../app/src/main/java/com/example/domain")
        srcDir("../app/src/main/java/com/example/network")
        srcDir("../app/src/main/java/com/example/data")
        exclude("**/AndroidSharedPrefsStorage.kt")
      }
    }
    test {
      kotlin {
        srcDir("src/test/kotlin")
      }
    }
  }
}

application {
  mainClass.set("com.example.windows.MainKt")
}

dependencies {
  implementation("com.squareup.retrofit2:retrofit:2.12.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.12.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
  implementation("com.squareup.okhttp3:okhttp:4.10.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.json:json:20240303")

  testImplementation("junit:junit:4.13.2")
}

tasks.register<Jar>("fatJar") {
  group = "build"
  description = "Assembles an executable fat JAR targeting Java 8 (Windows 7 32/64-bit compatible)."
  archiveBaseName.set("Meteotablo-desktop")
  archiveVersion.set("")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  manifest {
    attributes["Main-Class"] = "com.example.windows.MainKt"
    attributes["Implementation-Title"] = "Meteotablo"
    attributes["Implementation-Version"] = project.version
    attributes["Implementation-Vendor"] = "Allanur Saparow (@Allanur1995)"
  }

  from(sourceSets.main.get().output)
  dependsOn(configurations.runtimeClasspath)
  from({
    configurations.runtimeClasspath.get()
      .filter { it.name.endsWith(".jar") }
      .map { zipTree(it) }
  }) {
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class")
  }
}
