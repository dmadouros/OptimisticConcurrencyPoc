package me.dmadouros.optimistic.api

data class ChangeUserEmailAddressDto(
    val id: String?,
    val emailAddress: String?,
    val position: Long?
)
