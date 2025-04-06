package com.jana.functiontracer.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyDebugValue
import com.jana.functiontracer.model.FunctionStats
import java.util.concurrent.ConcurrentHashMap

@Service
class TracerService(private val project: Project) {
    private val logger = Logger.getInstance(TracerService::class.java)
    private var debugProcess: XDebugProcess? = null
    private var isTracing = false
    private val functionStats = ConcurrentHashMap<String, FunctionStats>()
    private val tracedFunctions = mutableSetOf<String>()

    companion object {
        @JvmStatic
        fun getInstance(project: Project): TracerService {
            return project.getService(TracerService::class.java)
        }
    }

    fun registerDebugProcess(process: XDebugProcess) {
        debugProcess = process
        logger.info("Debug process registered")
    }

    fun unregisterDebugProcess() {
        debugProcess = null
        if (isTracing) {
            stopTracing()
        }
        logger.info("Debug process unregistered")
    }

    fun isTracingActive(): Boolean {
        return isTracing
    }

    /**
     * Start tracing the specified functions.
     *
     * @param functions a set of function names"
     */
    fun startTracing(functions: Set<String>): Boolean {
        if (isTracing) {
            logger.info("Tracing already active, updating functions")
            return updateTracedFunctions(functions)
        }

        val process = debugProcess
        if (process == null) {
            logger.warn("No debug process available, cannot start tracing.")
            return false
        }
        if (process !is PyDebugProcess) {
            logger.warn("Debug process is not a Python debug process.")
            return false
        }

        tracedFunctions.clear()
        tracedFunctions.addAll(functions)
        functionStats.clear()

        for (funcName in functions) {
            functionStats[funcName] = FunctionStats(funcName)
        }

        try {
            val session = process.session
            val evaluator = session.debugProcess.evaluator

            val debugPyCheck = "import sys; 'debugpy' in sys.modules"
            evaluator?.evaluate(
                debugPyCheck,
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun evaluated(value: XValue) {
                        val pyValue = value as? PyDebugValue
                        val isDebugPy = pyValue?.value == "True"
                        if (isDebugPy) {
                            startTracingWithDebugPy(evaluator, functions)
                        } else {
                            startTracingWithPyDev(evaluator, functions)
                        }
                    }

                    override fun errorOccurred(errorMessage: String) {
                        logger.error("Error checking for DebugPy: $errorMessage")
                        // Fall back to PyDev approach
                        startTracingWithPyDev(evaluator, functions)
                    }
                },
                null
            )

            isTracing = true
            logger.info("Function tracing started for: ${functions.joinToString()}")
            return true
        } catch (e: Exception) {
            logger.error("Failed to start tracing", e)
            return false
        }
    }

    private fun startTracingWithDebugPy(
        evaluator: XDebuggerEvaluator?,
        functions: Set<String>
    ) {
        val tracerCode = this::class.java.classLoader
            .getResourceAsStream("python/tracer.py")
            ?.readBytes()
            ?.toString(Charsets.UTF_8)

        if (tracerCode == null) {
            logger.error("Could not load tracer.py from resources. Make sure it's in src/main/resources/python/")
            return
        }

        evaluator?.evaluate(
            tracerCode,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    // Now use the DAP command to start tracing
                    val startCmd = """
                        import debugpy
                        debugpy.request('functionTracer', {
                            'command': 'functionTracer_start',
                            'arguments': {'functionNames': ${functions.map { "'$it'" }.toList()}}
                        })
                    """.trimIndent()

                    evaluator.evaluate(
                        startCmd,
                        object : XDebuggerEvaluator.XEvaluationCallback {
                            override fun evaluated(value: XValue) {
                                logger.info("DebugPy tracing started successfully")
                            }
                            override fun errorOccurred(errorMessage: String) {
                                logger.error("Error starting DebugPy tracing: $errorMessage")
                            }
                        },
                        null
                    )
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error injecting tracer module: $errorMessage")
                }
            },
            null
        )
    }

    private fun startTracingWithPyDev(
        evaluator: XDebuggerEvaluator?,
        functions: Set<String>
    ) {
        // Inject the tracer module
        val tracerCode = this::class.java.classLoader
            .getResourceAsStream("python/tracer.py")
            ?.readBytes()
            ?.toString(Charsets.UTF_8)

        if (tracerCode == null) {
            logger.error("Could not load tracer.py from resources. Make sure it's in src/main/resources/python/")
            return
        }

        evaluator?.evaluate(
            tracerCode,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    // Now use the FunctionTracer directly
                    // (Dynamically imports each function by name and calls tracer.enable())
                    val startCmd = """
                        tracer = FunctionTracer.get_instance()
                        functions = []
                        for func_name in ${functions.map { "'$it'" }}:
                            try:
                                module_name, func_name = func_name.rsplit('.', 1)
                                module = __import__(module_name, fromlist=[func_name])
                                func = getattr(module, func_name)
                                functions.append(func)
                            except (ValueError, ImportError, AttributeError) as e:
                                print(f"Warning: Could not find function {func_name}: {e}")
                        
                        tracer.enable(functions)
                        print("[TracerService] PyDev tracer.enable() completed for", functions)
                    """.trimIndent()

                    evaluator.evaluate(
                        startCmd,
                        object : XDebuggerEvaluator.XEvaluationCallback {
                            override fun evaluated(value: XValue) {
                                logger.info("PyDev tracing started successfully.")
                            }
                            override fun errorOccurred(errorMessage: String) {
                                logger.error("Error starting PyDev tracing: $errorMessage")
                            }
                        },
                        null
                    )
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error injecting tracer module: $errorMessage")
                }
            },
            null
        )
    }

    fun stopTracing(): Map<String, FunctionStats> {
        if (!isTracing) {
            logger.info("Tracing not active, returning existing functionStats.")
            return functionStats
        }

        val process = debugProcess
        if (process == null || process !is PyDebugProcess) {
            isTracing = false
            logger.warn("Debug process not available when stopping tracing.")
            return functionStats
        }

        try {
            val session = process.session
            val evaluator = session.debugProcess.evaluator

            val debugPyCheck = "import sys; 'debugpy' in sys.modules"
            evaluator?.evaluate(
                debugPyCheck,
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun evaluated(value: XValue) {
                        val pyValue = value as? PyDebugValue
                        val isDebugPy = pyValue?.value == "True"
                        if (isDebugPy) {
                            stopTracingWithDebugPy(evaluator)
                        } else {
                            stopTracingWithPyDev(evaluator)
                        }
                    }
                    override fun errorOccurred(errorMessage: String) {
                        logger.error("Error checking for DebugPy: $errorMessage")
                        // Fall back to PyDev
                        stopTracingWithPyDev(evaluator)
                    }
                },
                null
            )

            isTracing = false
            logger.info("Function tracing stopped.")
            return functionStats
        } catch (e: Exception) {
            logger.error("Failed to stop tracing", e)
            isTracing = false
            return functionStats
        }
    }

    private fun stopTracingWithDebugPy(evaluator: XDebuggerEvaluator?) {
        val stopCmd = """
            import debugpy
            result = debugpy.request('functionTracer', {
                'command': 'functionTracer_stop'
            })
            result.get('results', {})
        """.trimIndent()

        evaluator?.evaluate(
            stopCmd,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    // Parse and update stats
                    try {
                        val pyValue = value as? PyDebugValue
                        val pyResults = pyValue?.getValue()
                        updateStatsFromPythonResults(pyResults)
                    } catch (e: Exception) {
                        logger.error("Error parsing DebugPy tracing results", e)
                    }
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error stopping DebugPy tracing: $errorMessage")
                }
            },
            null
        )
    }

    private fun stopTracingWithPyDev(evaluator: XDebuggerEvaluator?) {
        val stopCmd = """
            tracer = FunctionTracer.get_instance()
            results = tracer.disable()
            
            # Convert to serializable format
            serializable_results = {}
            for func, stats in results.items():
                if hasattr(func, '__name__'):
                    if hasattr(func, '__module__') and func.__module__ != '__main__':
                        func_name = f"{func.__module__}.{func.__name__}"
                    else:
                        func_name = func.__name__
                else:
                    func_name = str(func)
                
                serializable_results[func_name] = {
                    'call_count': stats.call_count,
                    'total_time': stats.total_time,
                    'avg_time': stats.avg_time,
                    'min_time': stats.min_time,
                    'max_time': stats.max_time
                }
            
            serializable_results
        """.trimIndent()

        evaluator?.evaluate(
            stopCmd,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    // Parse and update stats
                    try {
                        val pyValue = value as? PyDebugValue
                        val pyResults = pyValue?.getValue()
                        updateStatsFromPythonResults(pyResults)
                    } catch (e: Exception) {
                        logger.error("Error parsing PyDev tracing results", e)
                    }
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error stopping PyDev tracing: $errorMessage")
                }
            },
            null
        )
    }

    fun updateTracedFunctions(functions: Set<String>): Boolean {
        if (!isTracing) {
            logger.warn("Tracing not active, cannot update functions.")
            return false
        }

        val process = debugProcess
        if (process == null || process !is PyDebugProcess) {
            return false
        }

        tracedFunctions.clear()
        tracedFunctions.addAll(functions)

        for (funcName in functions) {
            if (!functionStats.containsKey(funcName)) {
                functionStats[funcName] = FunctionStats(funcName)
            }
        }

        try {
            val session = process.session
            val evaluator = session.debugProcess.evaluator

            val debugPyCheck = "import sys; 'debugpy' in sys.modules"
            evaluator?.evaluate(
                debugPyCheck,
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun evaluated(value: XValue) {
                        val pyValue = value as? PyDebugValue
                        val isDebugPy = pyValue?.value == "True"
                        if (isDebugPy) {
                            updateFunctionsWithDebugPy(evaluator, functions)
                        } else {
                            updateFunctionsWithPyDev(evaluator, functions)
                        }
                    }
                    override fun errorOccurred(errorMessage: String) {
                        logger.error("Error checking for DebugPy: $errorMessage")
                        // Fall back to PyDev
                        updateFunctionsWithPyDev(evaluator, functions)
                    }
                },
                null
            )

            logger.info("Updated traced functions: ${functions.joinToString()}")
            return true
        } catch (e: Exception) {
            logger.error("Failed to update traced functions", e)
            return false
        }
    }

    private fun updateFunctionsWithDebugPy(
        evaluator: XDebuggerEvaluator?,
        functions: Set<String>
    ) {
        val updateCmd = """
            import debugpy
            debugpy.request('functionTracer', {
                'command': 'functionTracer_update',
                'arguments': {'functionNames': ${functions.map { "'$it'" }.toList()}}
            })
        """.trimIndent()

        evaluator?.evaluate(
            updateCmd,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    logger.info("DebugPy function list updated successfully")
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error updating DebugPy functions: $errorMessage")
                }
            },
            null
        )
    }

    private fun updateFunctionsWithPyDev(
        evaluator: XDebuggerEvaluator?,
        functions: Set<String>
    ) {
        val updateCmd = """
            tracer = FunctionTracer.get_instance()
            functions_to_trace = []
            for func_name in ${functions.map { "'$it'" }}:
                try:
                    module_name, func_name = func_name.rsplit('.', 1)
                    module = __import__(module_name, fromlist=[func_name])
                    func = getattr(module, func_name)
                    functions_to_trace.append(func)
                except (ValueError, ImportError, AttributeError) as e:
                    print(f"Warning: Could not find function {func_name}: {e}")
            
            tracer.update_functions(functions_to_trace)
        """.trimIndent()

        evaluator?.evaluate(
            updateCmd,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    logger.info("PyDev function list updated successfully")
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error updating PyDev functions: $errorMessage")
                }
            },
            null
        )
    }

    fun refreshResults() {
        if (!isTracing) {
            logger.info("Tracing not active, no results to refresh.")
            return
        }

        val process = debugProcess
        if (process == null || process !is PyDebugProcess) {
            return
        }

        try {
            val session = process.session
            val evaluator = session.debugProcess.evaluator

            val debugPyCheck = "import sys; 'debugpy' in sys.modules"
            evaluator?.evaluate(
                debugPyCheck,
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun evaluated(value: XValue) {
                        val pyValue = value as? PyDebugValue
                        val isDebugPy = pyValue?.value == "True"

                        if (isDebugPy) {
                            getResultsWithDebugPy(evaluator)
                        } else {
                            getResultsWithPyDev(evaluator)
                        }
                    }
                    override fun errorOccurred(errorMessage: String) {
                        logger.error("Error checking for DebugPy: $errorMessage")
                        // Fall back to PyDev
                        getResultsWithPyDev(evaluator)
                    }
                },
                null
            )
        } catch (e: Exception) {
            logger.error("Failed to refresh tracing results", e)
        }
    }

    private fun getResultsWithDebugPy(evaluator: XDebuggerEvaluator?) {
        val getResultsCmd = """
            import debugpy
            result = debugpy.request('functionTracer', {
                'command': 'functionTracer_getResults'
            })
            result.get('results', {})
        """.trimIndent()

        evaluator?.evaluate(
            getResultsCmd,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    try {
                        val pyValue = value as? PyDebugValue
                        val pyResults = pyValue?.getValue()
                        updateStatsFromPythonResults(pyResults)
                    } catch (e: Exception) {
                        logger.error("Error parsing DebugPy results", e)
                    }
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error getting DebugPy results: $errorMessage")
                }
            },
            null
        )
    }

    private fun getResultsWithPyDev(evaluator: XDebuggerEvaluator?) {
        val getResultsCmd = """
            tracer = FunctionTracer.get_instance()
            results = tracer.get_results()
            
            # Convert to serializable format
            serializable_results = {}
            for func, stats in results.items():
                if hasattr(func, '__name__'):
                    if hasattr(func, '__module__') and func.__module__ != '__main__':
                        func_name = f"{func.__module__}.{func.__name__}"
                    else:
                        func_name = func.__name__
                else:
                    func_name = str(func)
                
                serializable_results[func_name] = {
                    'call_count': stats.call_count,
                    'total_time': stats.total_time,
                    'min_time': stats.min_time,
                    'max_time': stats.max_time
                }
            
            serializable_results
        """.trimIndent()

        evaluator?.evaluate(
            getResultsCmd,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(value: XValue) {
                    try {
                        val pyValue = value as? PyDebugValue
                        val pyResults = pyValue?.getValue()
                        updateStatsFromPythonResults(pyResults)
                    } catch (e: Exception) {
                        logger.error("Error parsing PyDev results", e)
                    }
                }
                override fun errorOccurred(errorMessage: String) {
                    logger.error("Error getting PyDev results: $errorMessage")
                }
            },
            null
        )
    }

    fun getFunctionStats(): Map<String, FunctionStats> {
        return functionStats
    }

    fun getTracedFunctions(): Set<String> {
        return tracedFunctions
    }

    private fun updateStatsFromPythonResults(pyResults: Any?) {
        if (pyResults !is Map<*, *>) return

        @Suppress("UNCHECKED_CAST")
        for ((funcName, statsMap) in pyResults as Map<String, Map<String, Any>>) {
            val stats = functionStats.computeIfAbsent(funcName) { FunctionStats(funcName) }
            stats.updateFromPython(statsMap)
        }
    }
}
