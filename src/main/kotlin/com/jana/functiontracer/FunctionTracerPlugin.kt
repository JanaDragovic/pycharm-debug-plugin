package com.jana.functiontracer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.jana.functiontracer.service.TracerService

@Service(Service.Level.APP)
class FunctionTracerPlugin : StartupActivity, ProjectManagerListener {
    private val logger = Logger.getInstance(FunctionTracerPlugin::class.java)

    init {
        logger.info("Python Function Tracer plugin instance created")

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(ProjectManager.TOPIC, this)
    }

    override fun runActivity(project: Project) {
        logger.info("FunctionTracer plugin runActivity called for project: ${project.name}")
        ensureServiceInitialized(project)
    }

    override fun projectOpened(project: Project) {
        logger.info("Project opened: ${project.name}")
        ensureServiceInitialized(project)
    }

    override fun projectClosed(project: Project) {
        logger.info("Project closed: ${project.name}")
    }

    private fun ensureServiceInitialized(project: Project) {
        try {
            val projectService = FunctionTracerProjectService.getInstance(project)
            val tracerService = TracerService.getInstance(project)
            logger.info("Successfully initialized services for project: ${project.name}")
        } catch (e: Exception) {
            logger.error("Failed to initialize services for project: ${project.name}", e)
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): FunctionTracerPlugin {
            return ApplicationManager.getApplication().getService(FunctionTracerPlugin::class.java)
        }
    }
}
