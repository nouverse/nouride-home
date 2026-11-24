import com.android.build.api.artifact.SingleArtifact
import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The version a release build carries: the repository's VERSION, one for both apps (`scripts/bump-version` changes
 * it; the release workflow refuses a tag that is not it). The version code grows with it, so Android takes each
 * release as an update of the one before: major × 1 000 000 + minor × 1 000 + patch.
 */
val releaseVersion: String = rootDir.resolve("../../VERSION").readText().trim()

fun versionCodeOf(version: String): Int {
  val parts = version.split('.').map { it.toInt() }
  require(parts.size == 3 && parts.all { it in 0..999 }) { "VERSION is not x.y.z: $version" }
  return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
}

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "tech.nouverse.nouride.android"
  compileSdk = 36

  defaultConfig {
    // The installed app's identity, fixed once anyone has it: "home" because this app makes the phone where Nouride
    // lives. A client app, if one comes, is `tech.nouverse.nouride`. The Kotlin package (`namespace`) is not the id.
    applicationId = "tech.nouverse.nouride.home"
    minSdk = 24
    // Termux's target. From 29 on, an app may not execute files in its data directory (W^X),
    // and proot maps every program of the Debian rootfs from there.
    targetSdk = 28
    versionCode = versionCodeOf(releaseVersion)
    versionName = releaseVersion
    // proot is a static aarch64 executable (scripts/build-proot.sh), needing a 64-bit kernel and nothing else. Most
    // Google TV and Android TV boxes run 64-bit kernels under a 32-bit userspace: they only install an APK with an
    // armeabi-v7a folder, then run aarch64 executables from it as any other (experiments/2026-11-24-android-tv/). The
    // same two files go in both folders (`Arm32Copy` below).
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    // Where the app looks for its own updates (update/Updates.kt). A test build may point it elsewhere.
    resValue("string", "update_feed", System.getenv("NOURIDE_HOME_UPDATE_FEED")
      ?: "https://api.github.com/repos/nouverse/nouride-home/releases/latest")
    // What a person's copy is: its name, Nouride's ports (Nouride.kt) and the link a browser comes back with after a
    // sign-in (CallbackCatcher, the manifest). The debug build below changes every one of them.
    resValue("string", "app_name", "Nouride Home")
    buildConfigField("int", "NOURIDE_PORT", "18254")
    buildConfigField("int", "GATEWAY_PORT", "18256")
    buildConfigField("String", "LINK_SCHEME", "\"nouridehome\"")
    manifestPlaceholders["linkScheme"] = "nouridehome"
  }

  buildFeatures { buildConfig = true }

  // One key for every release, kept by the owner and in the repository's secrets: Android installs an update only
  // when it is signed with the key of the app already on the phone. No key in the environment, no signed release.
  signingConfigs {
    create("release") {
      val keystore = System.getenv("NOURIDE_HOME_KEYSTORE")
      if (keystore != null) {
        storeFile = file(keystore)
        storePassword = System.getenv("NOURIDE_HOME_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("NOURIDE_HOME_KEY_ALIAS")
        keyPassword = System.getenv("NOURIDE_HOME_KEYSTORE_PASSWORD")
      }
    }
  }

  buildTypes {
    // A developer's build is another app beside the release on the same phone: its own id and name, so its own
    // Debian, Nouride and agents, on ports of its own, with its own way back from a sign-in. Testing it never stops,
    // replaces or answers for the Nouride a person runs. Never offered an update: its version is not x.y.z.
    debug {
      applicationIdSuffix = ".dev"
      resValue("string", "app_name", "Nouride Home Dev")
      buildConfigField("int", "NOURIDE_PORT", "18264")
      buildConfigField("int", "GATEWAY_PORT", "18266")
      buildConfigField("String", "LINK_SCHEME", "\"nouridehome-dev\"")
      manifestPlaceholders["linkScheme"] = "nouridehome-dev"
    }
    release {
      isMinifyEnabled = false
      if (System.getenv("NOURIDE_HOME_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
    }
  }

  packaging {
    // libproot.so and libproot-loader.so are executables: installed as files in
    // nativeLibraryDir, the one place the app may exec from, not mapped from the APK.
    jniLibs { useLegacyPackaging = true }
  }

  androidResources {
    // rootfs.tgz is gzip already.
    noCompress += "tgz"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    // The behaviour both apps share (spec/fixtures/, read by the desktop's Rust tests too): its folder, and a change
    // there runs the tests again.
    unitTests.all { test ->
      val spec = rootDir.resolve("../../spec")
      test.systemProperty("nouride.spec", spec.absolutePath)
      test.inputs.dir(spec).withPropertyName("spec")
    }
  }

  lint {
    // targetSdk 28 is deliberate (see above); the app is sideloaded, not published on Play.
    disable += "ExpiredTargetSdkVersion"
  }
}

/**
 * proot's two files under armeabi-v7a, copied from arm64-v8a where scripts/build-proot.sh puts them: one build, never a
 * second copy kept by hand. Android does not look at the ELF inside an ABI folder; it only picks the folder.
 */
abstract class Arm32Copy : DefaultTask() {
  @get:InputDirectory
  abstract val arm64: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun copy() {
    val into = outputDir.get().asFile.resolve("armeabi-v7a")
    into.deleteRecursively()
    into.mkdirs()
    val files = arm64.get().asFile.listFiles { f -> f.name.endsWith(".so") }.orEmpty()
    require(files.isNotEmpty()) { "no proot in ${arm64.get().asFile}: run scripts/build-proot.sh" }
    for (file in files) file.copyTo(into.resolve(file.name)).setExecutable(true, false)
  }
}

/**
 * The APK carries proot and its loader under both ABI folders, the same bytes in each, and no other native code: a
 * library built for one ABI only would be wrong in the other folder.
 */
abstract class ApkNativesCheck : DefaultTask() {
  @get:InputFiles
  abstract val apks: DirectoryProperty

  @TaskAction
  fun check() {
    val apk = apks.get().asFile.listFiles { f -> f.extension == "apk" }.orEmpty().singleOrNull()
      ?: throw GradleException("expected one APK in ${apks.get().asFile}")
    ZipFile(apk).use { zip ->
      val libs = zip.entries().asSequence().filter { it.name.startsWith("lib/") && !it.isDirectory }
        .associate { it.name to zip.getInputStream(it).use { input -> input.readBytes() } }
      val expected = listOf("arm64-v8a", "armeabi-v7a").flatMap { abi ->
        listOf("libproot.so", "libproot-loader.so").map { "lib/$abi/$it" }
      }.toSet()
      if (libs.keys != expected) throw GradleException("${apk.name} carries ${libs.keys.sorted()}, expected ${expected.sorted()}")
      for (name in listOf("libproot.so", "libproot-loader.so")) {
        if (!libs.getValue("lib/arm64-v8a/$name").contentEquals(libs.getValue("lib/armeabi-v7a/$name"))) {
          throw GradleException("${apk.name}: $name differs between arm64-v8a and armeabi-v7a")
        }
      }
    }
  }
}

androidComponents {
  onVariants { variant ->
    val name = variant.name.replaceFirstChar { it.uppercase() }
    val copy = tasks.register<Arm32Copy>("arm32Copy$name") {
      arm64.set(layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a"))
    }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(copy, Arm32Copy::outputDir)
    val check = tasks.register<ApkNativesCheck>("checkApkNatives$name") {
      apks.set(variant.artifacts.get(SingleArtifact.APK))
    }
    tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }
  }
}

// A developer's build stays `0.0.0-dev`, code 1, whatever VERSION says: not x.y.z, so it is never offered an update
// (update/UpdateFeed.kt), and any release installs over it.
androidComponents {
  onVariants(selector().withBuildType("debug")) { variant ->
    variant.outputs.forEach { output ->
      output.versionName.set("0.0.0-dev")
      output.versionCode.set(1)
    }
  }
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  // The real org.json for unit tests; android.jar only has stubs of it.
  testImplementation("org.json:json:20260814")
}
