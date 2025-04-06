package com.jana.functiontracer.debug

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.jetbrains.python.run.PythonConfigurationFactoryBase
import com.jetbrains.python.run.PythonRunConfiguration
import javax.swing.Icon

class FunctionTracerDebugConfigurationType : ConfigurationTypeBase(
    "PythonFunctionTracerDebug",
    "Python with Function Tracing",
    "Run/Debug Python with function tracing enabled",
    AllIcons.RunConfigurations.Application
) {
    init {
        addFactory(FunctionTracerConfigurationFactory(this))
    }

    private class FunctionTracerConfigurationFactory(type: FunctionTracerDebugConfigurationType) :
        PythonConfigurationFactoryBase(type) {

        override fun createTemplateConfiguration(project: Project): RunConfiguration {
            return FunctionTracerPythonRunConfiguration(project, this)
        }

        override fun getId(): String = "PythonFunctionTracerFactory"
    }

    private class FunctionTracerPythonRunConfiguration(project: Project, factory: ConfigurationFactory) :
        PythonRunConfiguration(project, factory) {
    }
}
