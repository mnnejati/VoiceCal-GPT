package ir.appointment.voice.voice.model

data class Candidate<T>(
    val value: T,
    val score: Int,
    val source: String
)
