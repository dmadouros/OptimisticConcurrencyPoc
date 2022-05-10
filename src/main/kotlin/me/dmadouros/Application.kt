package me.dmadouros

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.RecordedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import me.dmadouros.infrastructure.MessageStore
import me.dmadouros.optimistic.api.AddUserDto
import me.dmadouros.optimistic.api.ChangeUserEmailAddressDto
import me.dmadouros.optimistic.api.UserAddedDto
import me.dmadouros.optimistic.api.UserDto
import me.dmadouros.optimistic.api.UserEmailAddressChangedDto
import java.util.UUID

fun main() {
    val connectionString = "esdb://admin:changeit@localhost:2113?tls=false"
    val settings: EventStoreDBClientSettings = EventStoreDBConnectionString.parse(connectionString)
    val client: EventStoreDBClient = EventStoreDBClient.create(settings)
    val messageStore = MessageStore(client)
    val objectMapper = ObjectMapper().registerModule(KotlinModule())

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            jackson {}
        }

        routing {
            post("/addUser") {
                val addUserDto = call.receive<AddUserDto>()
                val userDto = UserDto(
                    id = addUserDto.id,
                    emailAddress = addUserDto.emailAddress!!
                )
                val userAddedDto = UserAddedDto(
                    data = userDto
                )
                messageStore.writeEvent("user", id = userDto.id!!, userAddedDto)

                call.respond(HttpStatusCode.Created, userAddedDto)
            }

            get("/users/{userId}") {
                val userId = call.parameters["userId"]
                val events = messageStore.readEvents("user", userId!!)

                val handleUserAdded = { _: UserDto, recordedEvent: RecordedEvent ->
                    objectMapper.readValue<UserAddedDto>(recordedEvent.eventData).data
                }
                val handleUserEmailAddress = { userDto: UserDto, recordedEvent: RecordedEvent ->
                    val event = objectMapper.readValue<UserEmailAddressChangedDto>(recordedEvent.eventData)
                    userDto.copy(emailAddress = event.data)
                }

                val init = UserDto(id = null, emailAddress = null)
                val projection = mapOf(
                    "UserAdded" to handleUserAdded,
                    "UserEmailAddressChanged" to handleUserEmailAddress
                )
                val userDto = events.fold(init) { memo, event ->
                    projection[event.eventType]?.let { handler -> handler(memo, event) } ?: memo
                }
                val lastEvent = messageStore.readLastEvent("user", userId)
                val lastPosition = lastEvent.streamRevision.valueUnsigned

                call.respond(HttpStatusCode.OK, userDto.copy(position = lastPosition))
            }

            post("/changeUserEmailAddressName") {
                val changeUserEmailAddressDto = call.receive<ChangeUserEmailAddressDto>()
                val lastEvent = messageStore.readLastEvent("user", changeUserEmailAddressDto.id!!)
                val lastPosition = lastEvent.streamRevision.valueUnsigned

                if (lastPosition > changeUserEmailAddressDto.position!!) {
                    call.respond(HttpStatusCode.BadRequest, "Optimistic Concurrency Exception")
                } else {
                    val userEmailAddressChangedDto = UserEmailAddressChangedDto(
                        data = changeUserEmailAddressDto.emailAddress!!
                    )
                    messageStore.writeEvent("user", id = changeUserEmailAddressDto.id, userEmailAddressChangedDto)

                    call.respond(HttpStatusCode.OK, userEmailAddressChangedDto)
                }
            }
        }
    }.start(wait = true)
}
