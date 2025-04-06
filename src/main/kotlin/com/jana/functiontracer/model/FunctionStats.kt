package com.jana.functiontracer.model


data class FunctionStats(
    val functionName: String,
    var callCount: Int = 0,
    var totalTime: Double = 0.0,  // Total execution time in seconds
    var minTime: Double = Double.MAX_VALUE,  // Minimum execution time
    var maxTime: Double = 0.0  // Maximum execution time
) {
    val avgTime: Double
        get() = if (callCount > 0) (totalTime / callCount) * 1000 else 0.0

    val formattedTotalTime: String
        get() = String.format("%.6f s", totalTime)

    val formattedAvgTime: String
        get() = String.format("%.6f ms", avgTime)

    val formattedMinTime: String
        get() = if (minTime == Double.MAX_VALUE) "-" else String.format("%.6f ms", minTime * 1000)

    val formattedMaxTime: String
        get() = String.format("%.6f ms", maxTime * 1000)

    fun updateFromPython(pyStats: Map<String, Any>): FunctionStats {
        callCount = (pyStats["call_count"] as? Number)?.toInt() ?: callCount
        totalTime = (pyStats["total_time"] as? Number)?.toDouble() ?: totalTime
        minTime = (pyStats["min_time"] as? Number)?.toDouble() ?: minTime
        maxTime = (pyStats["max_time"] as? Number)?.toDouble() ?: maxTime
        return this
    }
}
