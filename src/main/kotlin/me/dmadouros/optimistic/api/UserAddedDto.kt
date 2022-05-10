package me.dmadouros.optimistic.api

import me.dmadouros.optimistic.DomainEventDto

data class UserAddedDto(
    override val data: UserDto
) : DomainEventDto<UserDto>(type = "UserAdded", data = data)
