package org.kanga.tideserver

import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.MetricDatum
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.cloudwatch.model.StandardUnit.Count
import com.amazonaws.services.cloudwatch.model.StandardUnit.Microseconds
import java.time.Duration
import java.time.Instant

class Timer(val metrics: MutableList<MetricDatum>, val metricName: String,
            var multiDimensions: MutableCollection<Collection<Dimension>>)
{
    constructor(metrics: MutableList<MetricDatum>, metricName: String, vararg dimensions: Dimension):
        this(metrics, metricName, mutableListOf(dimensions.toList() as Collection<Dimension>))
    constructor(metrics: MutableList<MetricDatum>, metricName: String, multiDimensions: String):
        this(metrics, metricName, parseMultiDimensions(multiDimensions))

    inline fun<T> time(f: () -> T): T {
        val startTime = Instant.now()
        try {
            return f()
        } finally {
            val endTime = Instant.now()
            val elapsedMicros = 1e-3 * Duration.between(startTime, endTime).toNanos()

            multiDimensions.forEach { dimensions ->
                metrics.add(
                    MetricDatum().withMetricName(metricName).withDimensions(dimensions).withValue(elapsedMicros)
                        .withUnit(Microseconds))
            }
        }
    }
}

fun parseMultiDimensions(multiDimensions: String): MutableCollection<Collection<Dimension>> {
    return multiDimensions.split(';').map { parseDimensions(it) }.toMutableList()
}

fun parseDimensions(dimensions: String): Collection<Dimension> {
    val dlist = dimensions.split(',')
    val result = mutableListOf<Dimension>()

    dlist.forEach {
        val parts = it.split('=', limit=2)
        if (parts.size != 2) {
            log.error("Invalid dimension part $it in $dimensions")
        } else {
            result.add(Dimension().withName(parts[0]).withValue(parts[1]))
        }
    }

    return result
}

fun MutableCollection<MetricDatum>.add(name: String, value: Double, unit: StandardUnit=Count) {
    add(MetricDatum().withMetricName(name).withValue(value).withUnit(unit))
}

fun MutableCollection<MetricDatum>.add(name: String, value: Double, multiDimensions: String, unit: StandardUnit=Count) {
    parseMultiDimensions(multiDimensions).forEach {
        add(MetricDatum().withMetricName(name).withValue(value).withUnit(unit).withDimensions(it))
    }
}
