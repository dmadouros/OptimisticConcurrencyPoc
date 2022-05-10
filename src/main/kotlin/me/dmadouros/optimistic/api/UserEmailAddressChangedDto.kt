package me.dmadouros.optimistic.api

import me.dmadouros.optimistic.DomainEventDto

data class UserEmailAddressChangedDto(
    override val data: String
) : DomainEventDto<String>(type = "UserEmailAddressChanged", data = data)
