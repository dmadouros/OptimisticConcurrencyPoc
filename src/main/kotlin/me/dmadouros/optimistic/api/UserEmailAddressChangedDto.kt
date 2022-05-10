package me.dmadouros.optimistic.api

import me.dmadouros.optimistic.DomainEventDto

data class UserEmailAddressChangedDto(
    override val data: Data
) : DomainEventDto<UserEmailAddressChangedDto.Data>(type = "UserEmailAddressChanged", data = data) {
    data class Data(val id: String, val emailAddress: String)
}
