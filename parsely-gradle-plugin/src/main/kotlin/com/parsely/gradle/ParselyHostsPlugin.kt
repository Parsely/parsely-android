package com.parsely.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Resolves every declared Parse.ly site ID to its pixel host and writes the map into the
 * variant's generated assets, for [com.parsely.parselyandroid.PixelHosts] to read at runtime.
 *
 * The build fails whenever the declaration and Parse.ly disagree. The one exception is a
 * network failure: if the previously generated file already covers every declared site ID the
 * build warns and continues, so a Parse.ly outage does not break every customer's CI.
 */
abstract class BakeHostsTask : DefaultTask() {

    // Declared as InputFiles rather than InputFile so that a missing declaration reaches
    // the task action and produces an actionable message, instead of Gradle's generic
    // "an input file was expected to be present" validation error.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val declaration: ConfigurableFileCollection

    @get:Input
    abstract val endpoint: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun bake() {
        val mapper = ObjectMapper()
        val file = declaration.files.firstOrNull()
            ?: throw GradleException("[Parse.ly] No parsely-apikeys.json path was configured.")
        if (!file.exists()) {
            throw GradleException(
                "[Parse.ly] ${file.path} not found. Create it with {\"apikeys\": [\"yoursite.com\"]}."
            )
        }

        val declared = mapper.readTree(file).path("apikeys").map { it.asText() }.filter { it.isNotBlank() }
        if (declared.isEmpty()) {
            throw GradleException("[Parse.ly] ${file.path} declares no site IDs. Add every site ID your app tracks.")
        }

        val target = outputDirectory.get().asFile.resolve("parsely-hosts.json")
        val hosts = mutableMapOf<String, String>()
        var networkFailure: Exception? = null

        for (siteId in declared) {
            val url = "${endpoint.get()}/${URLEncoder.encode(siteId, "UTF-8")}/"
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                when (val code = connection.responseCode) {
                    200 -> hosts[siteId] = mapper.readTree(connection.inputStream).path("pixel_host").asText()
                    404 -> throw GradleException(
                        "[Parse.ly] Parse.ly does not recognise the site ID '$siteId'. " +
                            "Check it against your Parse.ly dashboard."
                    )
                    else -> throw GradleException("[Parse.ly] Parse.ly returned HTTP $code for '$siteId'.")
                }
            } catch (ex: GradleException) {
                throw ex
            } catch (ex: Exception) {
                networkFailure = ex
                break
            } finally {
                connection?.disconnect()
            }
        }

        if (networkFailure != null) {
            val covered = target.exists() && runCatching {
                val baked = mapper.readTree(target)
                baked.path("version").asInt() == 1 && declared.all { baked.path("hosts").has(it) }
            }.getOrDefault(false)

            if (covered) {
                logger.warn(
                    "[Parse.ly] Could not reach Parse.ly. Using the existing parsely-hosts.json, " +
                        "which covers every declared site ID."
                )
                return
            }
            throw GradleException(
                "[Parse.ly] Could not reach Parse.ly, and parsely-hosts.json does not cover every " +
                    "declared site ID.",
                networkFailure
            )
        }

        if (hosts.any { it.value.isBlank() }) {
            throw GradleException("[Parse.ly] Parse.ly returned no host for at least one declared site ID.")
        }

        target.parentFile.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            target,
            mapOf(
                "version" to 1,
                "generated_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)),
                "hosts" to hosts.toSortedMap(),
            )
        )
        logger.lifecycle("[Parse.ly] Wrote ${target.path}")
    }
}

class ParselyHostsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // React to the Android plugin whenever it lands, so `plugins {}` ordering does not
        // matter to the publisher. Applying this first used to fail with a confusing error.
        var wired = false
        listOf("com.android.application", "com.android.library").forEach { androidPluginId ->
            project.pluginManager.withPlugin(androidPluginId) {
                if (!wired) {
                    wired = true
                    registerTasks(project)
                }
            }
        }
        project.afterEvaluate {
            if (!wired) {
                throw GradleException(
                    "[Parse.ly] com.parsely.hosts needs an Android module: apply " +
                        "com.android.application or com.android.library in the same build file."
                )
            }
        }
    }

    private fun registerTasks(project: Project) {
        val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
        components.onVariants { variant ->
            val task = project.tasks.register(
                "parselyBakeHosts${variant.name.replaceFirstChar { it.uppercase() }}",
                BakeHostsTask::class.java
            ) { task ->
                task.declaration.setFrom(project.layout.projectDirectory.file("parsely-apikeys.json"))
                task.endpoint.set(
                    project.providers.gradleProperty("parsely.sdkConfigEndpoint")
                        .orElse("https://dash.parsely.com/api/jess/sdk-config")
                )
            }
            // AGP merges this into the APK's assets; nothing is ever written into src/.
            variant.sources.assets?.addGeneratedSourceDirectory(task, BakeHostsTask::outputDirectory)
        }
    }
}
