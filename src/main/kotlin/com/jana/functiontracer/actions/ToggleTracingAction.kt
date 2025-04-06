package com.jana.functiontracer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugProcess
import com.jana.functiontracer.service.TracerService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ToggleTracingAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val tracerService = TracerService.getInstance(project)

        if (tracerService.isTracingActive()) {
            val stats = tracerService.stopTracing()
            if (stats.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No functions were traced during this session.",
                    "Function Tracing Stopped"
                )
            } else {
                Messages.showInfoMessage(
                    project,
                    "Function tracing stopped. Traced ${stats.size} functions.\n" +
                            "See results in the Function Tracer tool window.",
                    "Function Tracing Stopped"
                )
            }
        } else {
            val dialog = FunctionTracerDialog(project, tracerService.getTracedFunctions())
            if (dialog.showAndGet()) {
                val functionsToTrace = dialog.getSelectedFunctions()
                if (functionsToTrace.isEmpty()) {
                    Messages.showWarningDialog(
                        project,
                        "No functions were selected for tracing.",
                        "Function Tracing"
                    )
                    return
                }

                val success = tracerService.startTracing(functionsToTrace)
                if (success) {
                    Messages.showInfoMessage(
                        project,
                        "Function tracing started for ${functionsToTrace.size} functions.",
                        "Function Tracing Started"
                    )
                } else {
                    Messages.showErrorDialog(
                        project,
                        "Failed to start function tracing. Make sure a Python debug session is active.",
                        "Function Tracing Error"
                    )
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        val presentation = e.presentation

        if (project == null) {
            presentation.isEnabled = false
            return
        }

        val debugSession = XDebuggerManager.getInstance(project).currentSession
        val isPythonDebugging = debugSession != null && debugSession.debugProcess is PyDebugProcess

        val tracerService = TracerService.getInstance(project)
        val isTracing = tracerService.isTracingActive()

        presentation.isEnabled = isPythonDebugging
        if (isTracing) {
            presentation.text = "Stop Function Tracing"
            presentation.description = "Stop tracing Python functions"
        } else {
            presentation.text = "Start Function Tracing"
            presentation.description = "Start tracing Python functions"
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    class FunctionTracerDialog(
        project: Project,
        initialFunctions: Set<String>
    ) : DialogWrapper(project) {
        private val functionListModel = DefaultListModel<String>()
        private val functionList = JBList(functionListModel)
        private val functionTextField = JBTextField()

        init {
            title = "Select Functions to Trace"
            init()

            initialFunctions.forEach { functionListModel.addElement(it) }
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())

            val inputPanel = JPanel(BorderLayout())
            inputPanel.add(functionTextField, BorderLayout.CENTER)

            val addButton = JButton("Add")
            addButton.addActionListener {
                val functionName = functionTextField.text.trim()
                if (functionName.isNotEmpty() && !functionListModel.contains(functionName)) {
                    functionListModel.addElement(functionName)
                    functionTextField.text = ""
                }
            }
            inputPanel.add(addButton, BorderLayout.EAST)

            val removeButton = JButton("Remove")
            removeButton.addActionListener {
                val selectedIndices = functionList.selectedIndices
                for (i in selectedIndices.size - 1 downTo 0) {
                    functionListModel.remove(selectedIndices[i])
                }
            }

            val listScrollPane = JBScrollPane(functionList)
            listScrollPane.preferredSize = Dimension(400, 300)

            val formBuilder = FormBuilder.createFormBuilder()
                .addLabeledComponent("Function to trace (e.g., module.function):", inputPanel)
                .addComponentToRightColumn(removeButton)
                .addComponentFillVertically(listScrollPane, 0)
                .panel

            panel.add(formBuilder, BorderLayout.CENTER)
            return panel
        }

        override fun doValidate(): ValidationInfo? {
            if (functionListModel.isEmpty) {
                return ValidationInfo("At least one function must be selected for tracing", functionTextField)
            }
            return null
        }

        fun getSelectedFunctions(): Set<String> {
            val functions = mutableSetOf<String>()
            for (i in 0 until functionListModel.size()) {
                functions.add(functionListModel.getElementAt(i))
            }
            return functions
        }
    }
}
