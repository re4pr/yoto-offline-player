import java.io.File
import java.net.URI
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
  val propsFile = rootProject.file("local.properties")
  if (propsFile.isFile) {
    propsFile.inputStream().use { load(it) }
  }
}

val configuredHubUrl: String = localProps.getProperty("yoto.hubUrl")
  ?.trim()
  ?.trimEnd('/')
  ?.takeIf { it.isNotEmpty() }
  ?: "http://192.168.1.100:9595"

val configuredHubHost: String = runCatching { URI(configuredHubUrl).host }
  .getOrNull()
  ?.takeIf { it.isNotBlank() }
  ?: "192.168.1.100"

val configuredAppDisplayName: String = localProps.getProperty("yoto.appName")
  ?.trim()
  ?.takeIf { it.isNotEmpty() }
  ?: "Yoto Offline Player"

fun escapeForJava(value: String): String =
  value.replace("\\", "\\\\").replace("\"", "\\\"")

fun escapeForXmlRes(value: String): String =
  value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\"", "\\\"")

abstract class GenerateNetworkSecurityConfigTask : DefaultTask() {
  @get:Input
  abstract val hubHost: Property<String>

  @get:OutputDirectory
  abstract val outputResDir: DirectoryProperty

  @TaskAction
  fun generate() {
    val xmlDir = File(outputResDir.get().asFile, "xml")
    xmlDir.mkdirs()
    val host = hubHost.get().trim().ifBlank { "192.168.1.100" }
    val escapedHost = host
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
    val domainEntries = buildString {
      appendLine("""        <domain includeSubdomains="false">$escapedHost</domain>""")
      if (host != "127.0.0.1") {
        appendLine("""        <domain includeSubdomains="false">127.0.0.1</domain>""")
      }
    }.trimEnd()
    File(xmlDir, "network_security_config.xml").writeText(
      """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
$domainEntries
    </domain-config>
</network-security-config>
""",
      Charsets.UTF_8,
    )
  }
}

android {
  namespace = "com.homelab.yototablet"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.homelab.yototablet"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "1.0.0"

    buildConfigField("String", "DEFAULT_HUB_URL", "\"${escapeForJava(configuredHubUrl)}\"")
    buildConfigField("String", "APP_DISPLAY_NAME", "\"${escapeForJava(configuredAppDisplayName)}\"")
    resValue("string", "app_name", escapeForXmlRes(configuredAppDisplayName))
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    resValues = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

androidComponents {
  onVariants { variant ->
    val capName = variant.name.replaceFirstChar { it.uppercase() }
    val genNetSecTask = tasks.register<GenerateNetworkSecurityConfigTask>(
      "generate${capName}NetworkSecurityConfig"
    ) {
      hubHost.set(configuredHubHost)
    }
    variant.sources.res?.addGeneratedSourceDirectory(
      genNetSecTask,
      GenerateNetworkSecurityConfigTask::outputResDir,
    )
  }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.work.runtime.ktx)

  testImplementation(libs.junit)
}
