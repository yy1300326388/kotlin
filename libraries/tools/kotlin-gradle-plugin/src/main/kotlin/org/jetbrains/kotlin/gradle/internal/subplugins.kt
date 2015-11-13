package org.jetbrains.kotlin.gradle.internal

import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.kotlinDebug
import java.util.*

internal fun loadSubplugins(project: Project): SubpluginEnvironment {
    try {
        val subplugins = ServiceLoader.load(
                KotlinGradleSubplugin::class.java, project.buildscript.classLoader).toList()

        val subpluginDependencyNames =
                subplugins.mapTo(hashSetOf<String>()) { it.getGroupName() + ":" + it.getArtifactName() }

        val classpath = project.buildscript.configurations.getByName("classpath")
        val subpluginClasspaths = hashMapOf<KotlinGradleSubplugin, List<String>>()

        for (subplugin in subplugins) {
            val files = classpath.dependencies
                    .filter { subpluginDependencyNames.contains(it.group + ":" + it.name) }
                    .flatMap { classpath.files(it).map { it.absolutePath } }
            subpluginClasspaths.put(subplugin, files)
        }

        return SubpluginEnvironment(subpluginClasspaths, subplugins)
    } catch (e: NoClassDefFoundError) {
        // Skip plugin loading if KotlinGradleSubplugin is not defined.
        // It is true now for tests in kotlin-gradle-plugin-core.
        return SubpluginEnvironment(mapOf(), listOf())
    }
}


class SubpluginEnvironment(
        val subpluginClasspaths: Map<KotlinGradleSubplugin, List<String>>,
        val subplugins: List<KotlinGradleSubplugin>
) {

    fun addSubpluginArguments(project: Project, compileTask: AbstractCompile) {
        val realPluginClasspaths = arrayListOf<String>()
        val pluginArguments = arrayListOf<String>()
        fun getPluginOptionString(pluginId: String, key: String, value: String) = "plugin:$pluginId:$key=$value"

        subplugins.forEach { subplugin ->
            val args = subplugin.getExtraArguments(project, compileTask)

            with (subplugin) {
                project.logger.kotlinDebug("Subplugin ${getPluginName()} (${getGroupName()}:${getArtifactName()}) loaded.")
            }

            val subpluginClasspath = subpluginClasspaths[subplugin]
            if (args != null && subpluginClasspath != null) {
                realPluginClasspaths.addAll(subpluginClasspath)
                for (arg in args) {
                    val option = getPluginOptionString(subplugin.getPluginName(), arg.key, arg.value)
                    pluginArguments.add(option)
                }
            }
        }

        compileTask.extensions.extraProperties.apply {
            set("compilerPluginClasspaths", realPluginClasspaths.toTypedArray())
            set("compilerPluginArguments", pluginArguments.toTypedArray())
        }
    }
}
