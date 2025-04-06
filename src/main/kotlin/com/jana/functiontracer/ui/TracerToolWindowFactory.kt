package com.jana.functiontracer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.jana.functiontracer.model.FunctionStats
import com.jana.functiontracer.service.TracerService
import com.jana.functiontracer.actions.ToggleTracingAction
import java.awt.BorderLayout
import java.util.Timer
import java.util.TimerTask
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel


class TracerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val tracerToolWindow = TracerToolWindow(project)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(tracerToolWindow.content, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class TracerToolWindow(private val project: Project) {
        val content: JPanel = JPanel(BorderLayout())
        private val tracerService = TracerService.getInstance(project)
        private val statsTableModel = FunctionStatsTableModel()
        private val statsTable = JBTable(statsTableModel)
        private var refreshTimer: Timer? = null

        init {
            val toolbar = JToolBar()
            toolbar.isFloatable = false

            val startButton = JButton("Start Tracing")
            startButton.addActionListener {
                val dialog = ToggleTracingAction.FunctionTracerDialog(
                    project, tracerService.getTracedFunctions()
                )
                if (dialog.showAndGet()) {
                    val functionsToTrace = dialog.getSelectedFunctions()
                    if (functionsToTrace.isNotEmpty()) {
                        tracerService.startTracing(functionsToTrace)
                        startRefreshTimer()
                        updateUI()
                    }
                }
            }
            toolbar.add(startButton)

            val stopButton = JButton("Stop Tracing")
            stopButton.addActionListener {
                tracerService.stopTracing()
                stopRefreshTimer()
                updateUI()
            }
            toolbar.add(stopButton)

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener {
                tracerService.refreshResults()
                updateUI()
            }
            toolbar.add(refreshButton)

            val clearButton = JButton("Clear")
            clearButton.addActionListener {
                statsTableModel.clearStats()
            }
            toolbar.add(clearButton)

            statsTable.autoCreateRowSorter = true
            statsTable.fillsViewportHeight = true

            content.add(toolbar, BorderLayout.NORTH)
            content.add(JBScrollPane(statsTable), BorderLayout.CENTER)

            updateUI()
        }

        private fun startRefreshTimer() {
            stopRefreshTimer()
            refreshTimer = Timer(true).apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        tracerService.refreshResults()
                        SwingUtilities.invokeLater { updateUI() }
                    }
                }, 1000, 1000) // Refresh every second
            }
        }

        private fun stopRefreshTimer() {
            refreshTimer?.cancel()
            refreshTimer = null
        }


        private fun updateUI() {
            val isTracing = tracerService.isTracingActive()
            statsTableModel.updateStats(tracerService.getFunctionStats())

            // Update buttons based on tracing state
            SwingUtilities.invokeLater {
                val toolbar = content.getComponent(0) as JToolBar
                val startButton = toolbar.getComponent(0) as JButton
                val stopButton = toolbar.getComponent(1) as JButton

                startButton.isEnabled = !isTracing
                stopButton.isEnabled = isTracing
            }
        }
    }

    private class FunctionStatsTableModel : AbstractTableModel() {
        private val columns = arrayOf("Function", "Calls", "Total Time (s)", "Avg Time (ms)", "Min Time (ms)", "Max Time (ms)")
        private val stats = mutableListOf<FunctionStats>()

        override fun getRowCount(): Int = stats.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val stat = stats[rowIndex]
            return when (columnIndex) {
                0 -> stat.functionName
                1 -> stat.callCount
                2 -> stat.formattedTotalTime
                3 -> stat.formattedAvgTime
                4 -> stat.formattedMinTime
                5 -> stat.formattedMaxTime
                else -> ""
            }
        }

        fun updateStats(newStats: Map<String, FunctionStats>) {
            stats.clear()
            stats.addAll(newStats.values)
            fireTableDataChanged()
        }

        fun clearStats() {
            stats.clear()
            fireTableDataChanged()
        }
    }
}
