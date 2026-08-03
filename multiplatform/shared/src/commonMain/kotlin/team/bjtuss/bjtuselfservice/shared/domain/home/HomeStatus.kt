package team.bjtuss.bjtuselfservice.shared.domain.home

data class HomeStatus(
    val newMailCount: String,
    val campusCardBalance: String,
    val networkBalance: String,
) {
    val hasNewMail: Boolean get() = newMailCount.toIntOrNull()?.let { it > 0 } == true
    val campusCardLow: Boolean get() = campusCardBalance.toDoubleOrNull()?.let { it < 20.0 } == true
    val networkEmpty: Boolean get() = networkBalance.toDoubleOrNull()?.let { it == 0.0 } == true
}
