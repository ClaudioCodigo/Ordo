package dev.claudiocodigo.nexo.domain.time

import javax.inject.Inject

interface ClockProvider {
    fun nowMillis(): Long
}

class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
