package tech.dm.conso.data

fun bucketFor(spanMs: Long): Long = (spanMs / 60).coerceAtLeast(1_000L).let { ((it + 999) / 1_000) * 1_000 }

fun downsample(samples: List<Sample>, bucketMs: Long): List<Sample> {
    if (bucketMs <= 1_000L) return samples
    return samples.groupBy { it.wallMs / bucketMs }.map { (bucket, group) ->
        val withFuel = group.filter { it.litersPerHour != null }
        val basis = withFuel.ifEmpty { group }
        Sample(
            wallMs = bucket * bucketMs + bucketMs / 2,
            speedKmh = basis.sumOf { it.speedKmh } / basis.size,
            litersPerHour = withFuel.takeIf { it.isNotEmpty() }?.sumOf { it.litersPerHour!! }?.div(withFuel.size),
            altitudeM = group.mapNotNull { it.altitudeM }.takeIf { it.isNotEmpty() }?.average(),
            grade = group.sumOf { it.grade } / group.size,
            ecoScore = group.mapNotNull { it.ecoScore }.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
