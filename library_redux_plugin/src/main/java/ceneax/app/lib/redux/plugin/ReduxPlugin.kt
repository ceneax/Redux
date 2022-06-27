package ceneax.app.lib.redux.plugin

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class ReduxPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                val task = project.tasks.register("clean${variant.name.capitalize()}ReduxModule", CleanModuleMapTask::class.java) {}
                val cleanTask = project.tasks.named("clean")
                cleanTask.dependsOn(task)

                variant.transformClassesWith(ModuleClassVisitorFactory::class.java, InstrumentationScope.ALL) {
                    it.invalidate.setDisallowChanges(System.currentTimeMillis())
                }
                variant.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
            }
        }
    }
}

abstract class CleanModuleMapTask : DefaultTask() {
    @TaskAction
    fun action() {
        ModuleHolder.clearModule()
    }
}