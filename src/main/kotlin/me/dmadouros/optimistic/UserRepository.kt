package me.dmadouros.optimistic

import com.eventstore.dbclient.RecordedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.dmadouros.infrastructure.MessageStore
import me.dmadouros.optimistic.api.UserAddedDto
import me.dmadouros.optimistic.api.UserDto
import me.dmadouros.optimistic.api.UserEmailAddressChangedDto

class UserRepository(private val messageStore: MessageStore, private val objectMapper: ObjectMapper) {
    fun findById(id: String): UserDto {
        val handleUserAdded = { _: UserDto, recordedEvent: RecordedEvent ->
            objectMapper.readValue<UserAddedDto>(recordedEvent.eventData).data
        }
        val handleUserEmailAddress = { userDto: UserDto, recordedEvent: RecordedEvent ->
            val event = objectMapper.readValue<UserEmailAddressChangedDto>(recordedEvent.eventData)
            userDto.copy(emailAddress = event.data.emailAddress)
        }

        val init = UserDto(id = "", emailAddress = "", position = 0L)

        val projection = mapOf(
            "UserAdded" to handleUserAdded,
            "UserEmailAddressChanged" to handleUserEmailAddress
        )

        val events = messageStore.readEvents("user", id)
        val userDto = events.fold(init) { memo, event ->
            projection[event.eventType]?.let { handler -> handler(memo, event) } ?: memo
        }
        
        val lastEvent = events.last()
        val lastPosition = lastEvent.streamRevision.valueUnsigned

        return userDto.copy(position = lastPosition)
    }
}
