package com.jana.functiontracer.debug

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.python.debugger.PyDebugProcess
import com.jana.functiontracer.service.TracerService

class DebugProcessListener : XDebuggerManagerListener {
    private val logger = Logger.getInstance(DebugProcessListener::class.java)

    override fun processStarted(debugProcess: XDebugProcess) {
        if (debugProcess is PyDebugProcess) {
            val project = debugProcess.session.project
            val tracerService = TracerService.getInstance(project)

            logger.info("Python debug process started")
            tracerService.registerDebugProcess(debugProcess)
        }
    }

    override fun processStopped(event: XDebugProcess) {
        if (event is PyDebugProcess) {
            val project = event.session.project
            val tracerService = TracerService.getInstance(project)

            logger.info("Python debug process stopped")
            tracerService.unregisterDebugProcess()
        }
    }

    override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
        if (currentSession != null && currentSession.debugProcess is PyDebugProcess) {
            val project = currentSession.project
            val tracerService = TracerService.getInstance(project)

            logger.info("Current debug session changed to Python")
            tracerService.registerDebugProcess(currentSession.debugProcess)
        }
    }
}
