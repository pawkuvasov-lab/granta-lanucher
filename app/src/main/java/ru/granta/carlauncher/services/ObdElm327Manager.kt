package ru.granta.carlauncher.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CarObdState(
    val coolantTempC: Int = 88,
    val batteryVoltage: Float = 14.2f,
    val engineRpm: Int = 840,
    val fuelLevelPercent: Int = 68,
    val avgConsumptionL100: Float = 6.8f,
    val doorsLocked: Boolean = true,
    val lowBeamOn: Boolean = true,
    val dtcCodesCount: Int = 0
)

object ObdElm327Manager {
    private val _obdState = MutableStateFlow(CarObdState())
    val obdState: StateFlow<CarObdState> = _obdState

    // Парсинг стандартных PID-ответов ELM327 (01 05 - ОЖ, 01 0C - Обороты, ATRV - Напряжение)
    fun parseElmResponse(rawCmd: String, rawHex: String) {
        val clean = rawHex.replace(" ", "").trim()
        when (rawCmd) {
            "0105" -> if (clean.length >= 6) {
                val temp = clean.substring(4, 6).toInt(16) - 40
                _obdState.value = _obdState.value.copy(coolantTempC = temp)
            }
            "010C" -> if (clean.length >= 8) {
                val a = clean.substring(4, 6).toInt(16)
                val b = clean.substring(6, 8).toInt(16)
                val rpm = ((a * 256) + b) / 4
                _obdState.value = _obdState.value.copy(engineRpm = rpm)
            }
        }
    }
}
