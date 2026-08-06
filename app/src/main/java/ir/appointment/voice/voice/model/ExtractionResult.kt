package ir.appointment.voice.voice.model

data class ExtractionResult(
    val person: Candidate<String>? = null,
    val location: Candidate<String>? = null,
    val title: Candidate<String>? = null,
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val weekday: String? = null,
    val hour: Int? = null,
    val minute: Int? = null
)
