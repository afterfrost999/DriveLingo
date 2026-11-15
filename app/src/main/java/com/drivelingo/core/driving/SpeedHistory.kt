package com.drivelingo.core.driving

/**
 * 최근 속도 이력. "가다 서다" 는 순간 속도로는 알 수 없어서 일정 시간 동안의 패턴으로 판단한다.
 */
class SpeedHistory(private val windowMs: Long = 60_000) {

    private data class Sample(val atMs: Long, val speedKmh: Float)

    private val samples = ArrayDeque<Sample>()

    fun add(atMs: Long, speedKmh: Float?) {
        if (speedKmh != null) samples.addLast(Sample(atMs, speedKmh))
        while (samples.isNotEmpty() && atMs - samples.first().atMs > windowMs) samples.removeFirst()
    }

    fun clear() = samples.clear()

    /**
     * 거의 멈춤(≤3km/h) → 출발(≥15km/h) 전환이 창 안에서 [minCycles] 번 이상이면 가다 서다.
     * 신호등 한 번 정차 후 출발(1~2회)은 가다 서다로 보지 않는다.
     */
    fun isStopAndGo(minCycles: Int = 3): Boolean {
        var cycles = 0
        var wasStopped = false
        for (sample in samples) {
            if (sample.speedKmh <= STOPPED_KMH) {
                wasStopped = true
            } else if (sample.speedKmh >= MOVING_KMH && wasStopped) {
                cycles++
                wasStopped = false
            }
        }
        return cycles >= minCycles
    }

    private companion object {
        const val STOPPED_KMH = 3f
        const val MOVING_KMH = 15f
    }
}
