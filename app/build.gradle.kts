import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifySha256Task : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifacts: ConfigurableFileCollection

    @get:Input
    abstract val expectedSha256: Property<String>

    @TaskAction
    fun verify() {
        val runtimeFile = artifacts.singleFile
        val digest = MessageDigest.getInstance("SHA-256")
        runtimeFile.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val expected = expectedSha256.get()
        check(actualSha256 == expected) {
            "Unexpected sherpa-onnx runtime checksum for ${runtimeFile.name}: " +
                "expected $expected, got $actualSha256"
        }
    }
}

data class PinnedSenseVoiceArtifact(
    val fileName: String,
    val length: Long,
    val sha256: String,
)

abstract class PrepareSenseVoiceAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val legalDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedAssetsDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val source = sourceDirectory.asFile.get()
        if (!source.isDirectory) {
            throw GradleException(
                "SenseVoice model source is unavailable; set -Ppercontext.sensevoiceModelDir " +
                    "or populate the local model convention.",
            )
        }

        val legal = legalDirectory.asFile.get()
        if (!legal.isDirectory) {
            throw GradleException("SenseVoice legal notices are unavailable.")
        }
        PINNED_MODEL_ARTIFACTS.forEach { artifact -> verify(source, artifact) }
        PINNED_LEGAL_ARTIFACTS.forEach { artifact -> verify(legal, artifact) }

        val output = generatedAssetsDirectory.asFile.get()
        val staging = output.resolveSibling("${output.name}.staging")
        staging.deleteRecursively()
        val packAssets = staging.resolve("$ASSET_PARENT_DIRECTORY/$PACK_DIRECTORY")
        if (!packAssets.mkdirs()) {
            throw GradleException("Cannot create generated SenseVoice asset directory.")
        }
        (PINNED_MODEL_ARTIFACTS + PINNED_LEGAL_ARTIFACTS).forEach { artifact ->
            val artifactSource = if (artifact in PINNED_LEGAL_ARTIFACTS) legal else source
            try {
                artifactSource.resolve(artifact.fileName).copyTo(
                    target = packAssets.resolve(artifact.fileName),
                    overwrite = true,
                )
            } catch (_: Exception) {
                throw GradleException("Cannot stage SenseVoice asset '${artifact.fileName}'.")
            }
        }
        PINNED_ARTIFACTS.forEach { artifact -> verify(packAssets, artifact) }
        try {
            packAssets.resolve(MANIFEST_FILE_NAME).writeText(manifestJson())
        } catch (_: Exception) {
            throw GradleException("Cannot write the generated SenseVoice manifest.")
        }

        if (output.exists() && !output.deleteRecursively()) {
            throw GradleException("Cannot replace generated SenseVoice assets.")
        }
        if (!staging.renameTo(output)) {
            throw GradleException("Cannot publish generated SenseVoice assets.")
        }
    }

    private fun verify(source: java.io.File, artifact: PinnedSenseVoiceArtifact) {
        val file = source.resolve(artifact.fileName)
        if (!file.isFile) {
            throw GradleException("SenseVoice source is missing '${artifact.fileName}'.")
        }
        if (file.length() != artifact.length) {
            throw GradleException(
                "SenseVoice source '${artifact.fileName}' has length ${file.length()}; " +
                    "expected ${artifact.length}.",
            )
        }
        val actualSha256 = try {
            file.sha256()
        } catch (_: Exception) {
            throw GradleException("Cannot read SenseVoice source '${artifact.fileName}'.")
        }
        if (actualSha256 != artifact.sha256) {
            throw GradleException(
                "SenseVoice source '${artifact.fileName}' has SHA-256 $actualSha256; " +
                    "expected ${artifact.sha256}.",
            )
        }
    }

    private fun manifestJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schema_version\": 1,")
        appendLine("  \"pack_version\": \"sensevoice-int8-2024-07-17\",")
        appendLine("  \"directory\": \"$PACK_DIRECTORY\",")
        appendLine("  \"files\": [")
        PINNED_ARTIFACTS.forEachIndexed { index, artifact ->
            val comma = if (index == PINNED_ARTIFACTS.lastIndex) "" else ","
            appendLine(
                "    {\"name\": \"${artifact.fileName}\", \"length\": ${artifact.length}, " +
                    "\"sha256\": \"${artifact.sha256}\"}$comma",
            )
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun java.io.File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val ASSET_PARENT_DIRECTORY = "sensevoice"
        const val PACK_DIRECTORY =
            "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
        const val MANIFEST_FILE_NAME = "manifest.json"

        val PINNED_MODEL_ARTIFACTS = listOf(
            PinnedSenseVoiceArtifact(
                fileName = "model.int8.onnx",
                length = 239_233_841L,
                sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
            ),
            PinnedSenseVoiceArtifact(
                fileName = "tokens.txt",
                length = 315_894L,
                sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
            ),
        )
        val PINNED_LEGAL_ARTIFACTS = listOf(
            PinnedSenseVoiceArtifact(
                fileName = "MODEL_LICENSE",
                length = 5_306L,
                sha256 = "7dba975a2069691db4992b0592d70828b330d2f8a30a71450f4e152a554e84f8",
            ),
            PinnedSenseVoiceArtifact(
                fileName = "NOTICE",
                length = 744L,
                sha256 = "7e469c226b755d6ac46e3397023dd5950ca06e13bfae5ac831d07f4b855c18e5",
            ),
        )
        val PINNED_ARTIFACTS = PINNED_MODEL_ARTIFACTS + PINNED_LEGAL_ARTIFACTS
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val sherpaOnnxRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val sherpaOnnxCoordinate =
    "com.k2fsa.sherpa.onnx:sherpa-onnx:${libs.versions.sherpaOnnx.get()}@aar"
val sherpaOnnxSha256 = "aa5505c0ec4f8bdaee5f214a64ba3012be64f2aecc022e82a64f33392b8dd245"
val senseVoicePackDirectory =
    "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
val generatedSenseVoiceAssets = layout.buildDirectory.dir("generated/sensevoice-assets/main")
val releaseSigningFile = rootProject.file("signing.local.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use(::load)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.percontext.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.percontext.community"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.2-community"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("androidTest").assets.directories.add(
        "$projectDir/schemas",
    )
    sourceSets.getByName("main").assets.directories.add(
        generatedSenseVoiceAssets.get().asFile.absolutePath,
    )

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("communityRelease") {
                fun required(name: String) = releaseSigningProperties.getProperty(name)
                    ?.takeIf(String::isNotBlank) ?: error("Release signing configuration is incomplete")
                storeFile = rootProject.file(required("storeFile"))
                storePassword = required("storePassword")
                keyAlias = required("keyAlias")
                keyPassword = required("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("communityRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(sherpaOnnxCoordinate)
    sherpaOnnxRuntime(sherpaOnnxCoordinate)
    ksp(libs.androidx.room3.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room3.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val verifySherpaOnnxRuntime by tasks.registering(VerifySha256Task::class) {
    group = "verification"
    description = "Verifies the pinned sherpa-onnx Android runtime artifact."
    artifacts.from(sherpaOnnxRuntime)
    expectedSha256.set(sherpaOnnxSha256)
}

val prepareBundledSenseVoiceAssets by tasks.registering(PrepareSenseVoiceAssetsTask::class) {
    group = "build"
    description = "Validates and prepares the pinned SenseVoice model APK assets."
    val configuredSource = providers.gradleProperty("percontext.sensevoiceModelDir").orNull
    sourceDirectory.set(
        if (configuredSource.isNullOrBlank()) {
            rootProject.file("local-models/models/$senseVoicePackDirectory")
        } else {
            rootProject.file(configuredSource)
        },
    )
    legalDirectory.set(layout.projectDirectory.dir("src/main/sensevoice-legal"))
    generatedAssetsDirectory.set(generatedSenseVoiceAssets)
}

tasks.named("preBuild") {
    dependsOn(verifySherpaOnnxRuntime)
    dependsOn(prepareBundledSenseVoiceAssets)
}
