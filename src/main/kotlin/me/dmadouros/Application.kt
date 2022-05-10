package me.dmadouros

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
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
import me.dmadouros.optimistic.UserRepository
import me.dmadouros.optimistic.api.AddUserDto
import me.dmadouros.optimistic.api.ChangeUserEmailAddressDto
import me.dmadouros.optimistic.workflows.AddUserWorkflow
import me.dmadouros.optimistic.workflows.ChangeUserEmailAddressWorkflow
import me.dmadouros.optimistic.workflows.FindUserWorkflow

fun main() {
    val connectionString = "esdb://admin:changeit@localhost:2113?tls=false"
    val settings: EventStoreDBClientSettings = EventStoreDBConnectionString.parse(connectionString)
    val client: EventStoreDBClient = EventStoreDBClient.create(settings)
    val messageStore = MessageStore(client)
    val objectMapper = ObjectMapper().registerModule(KotlinModule())
    val addUserWorkflow = AddUserWorkflow()
    val changeUserEmailAddressWorkflow = ChangeUserEmailAddressWorkflow(messageStore)
    val userRepository = UserRepository(messageStore, objectMapper)
    val findUserWorkflow = FindUserWorkflow(userRepository)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            jackson {}
        }

        routing {
            post("/addUser") {
                val addUserDto = call.receive<AddUserDto>()
                val userAddedDto = addUserWorkflow.exec(addUserDto)
                messageStore.writeEvent("user", id = userAddedDto.data.id!!, userAddedDto)

                call.respond(HttpStatusCode.Created, userAddedDto)
            }

            get("/users/{userId}") {
                val userId = call.parameters["userId"]
                val userDto = findUserWorkflow.exec(userId!!)

                call.respond(HttpStatusCode.OK, userDto)
            }

            post("/changeUserEmailAddressName") {
                val changeUserEmailAddressDto = call.receive<ChangeUserEmailAddressDto>()

                changeUserEmailAddressWorkflow.exec(changeUserEmailAddressDto)
                    .onSuccess { messageStore.writeEvent("user", id = it.data.id, it) }
                    .onSuccess { call.respond(HttpStatusCode.OK, it) }
                    .onFailure { call.respond(HttpStatusCode.BadRequest, it) }
            }
        }
    }.start(wait = true)
}
