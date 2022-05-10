package me.dmadouros.optimistic.workflows

import me.dmadouros.optimistic.api.AddUserDto
import me.dmadouros.optimistic.api.UserAddedDto
import me.dmadouros.optimistic.api.UserDto

class AddUserWorkflow {
    fun exec(addUserDto: AddUserDto): UserAddedDto =
        createEvent(addUserDto)

    private fun createEvent(addUserDto: AddUserDto) =
        UserAddedDto(
            data = UserDto(
                id = addUserDto.id!!,
                emailAddress = addUserDto.emailAddress!!
            )
        )
}
