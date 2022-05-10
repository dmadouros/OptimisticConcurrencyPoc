package me.dmadouros.optimistic.workflows

import me.dmadouros.optimistic.UserRepository
import me.dmadouros.optimistic.api.UserDto

class FindUserWorkflow(private val userRepository: UserRepository) {
    fun exec(userId: String): UserDto =
        userRepository.findById(userId)
}
