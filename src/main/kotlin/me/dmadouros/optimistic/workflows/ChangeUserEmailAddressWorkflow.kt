package me.dmadouros.optimistic.workflows

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import me.dmadouros.infrastructure.MessageStore
import me.dmadouros.optimistic.api.ChangeUserEmailAddressDto
import me.dmadouros.optimistic.api.UserEmailAddressChangedDto

class ChangeUserEmailAddressWorkflow(private val messageStore: MessageStore) {
    fun exec(
        changeUserEmailAddressDto: ChangeUserEmailAddressDto
    ): Result<UserEmailAddressChangedDto, String> =
        ensureNoVersionConflict(changeUserEmailAddressDto)
            .map(this::createEvent)

    private fun ensureNoVersionConflict(changeUserEmailAddressDto: ChangeUserEmailAddressDto): Result<ChangeUserEmailAddressDto, String> {
        val lastEvent = messageStore.readLastEvent("user", changeUserEmailAddressDto.id!!)
        val actualPosition = lastEvent.streamRevision.valueUnsigned
        val expectedPosition = changeUserEmailAddressDto.position!!

        return if (actualPosition == expectedPosition) {
            Ok(changeUserEmailAddressDto)
        } else {
            Err("Version conflict. Expected version: $expectedPosition. Actual version: $actualPosition")
        }
    }

    private fun createEvent(changeUserEmailAddressDto: ChangeUserEmailAddressDto) =
        UserEmailAddressChangedDto(
            data = UserEmailAddressChangedDto.Data(
                id = changeUserEmailAddressDto.id!!,
                emailAddress = changeUserEmailAddressDto.emailAddress!!
            )
        )
}
