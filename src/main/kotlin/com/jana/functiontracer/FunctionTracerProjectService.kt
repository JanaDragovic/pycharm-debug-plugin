package com.jana.functiontracer

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jana.functiontracer.service.TracerService

@Service
class FunctionTracerProjectService(private val project: Project) {
    private val logger = Logger.getInstance(FunctionTracerProjectService::class.java)

    init {
        logger.info("Python Function Tracer plugin initialized for project: ${project.name}")

        TracerService.getInstance(project)
    }

    companion object {
        fun getInstance(project: Project): FunctionTracerProjectService {
            return project.getService(FunctionTracerProjectService::class.java)
        }
    }
}
