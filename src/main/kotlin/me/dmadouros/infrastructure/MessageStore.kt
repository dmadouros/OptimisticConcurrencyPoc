package me.dmadouros.infrastructure

import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.ReadAllOptions
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.RecordedEvent
import me.dmadouros.optimistic.DomainEventDto
import java.util.UUID

class MessageStore(private val client: EventStoreDBClient) {
    fun <T> writeEvent(category: String, id: String, event: DomainEventDto<T>) {
        val eventData = EventData
            .builderAsJson(UUID.fromString(event.id), event.type, event)
            .build()

        client.appendToStream("$category-$id", eventData).get()
    }

    fun readEvents(category: String, id: String): List<RecordedEvent> {
        val options = ReadStreamOptions.get()
            .forwards()
            .fromStart()

        return client.readStream("$category-$id", options).get()
            .events
            .map { it.originalEvent }
    }

    fun readLastEvent(category: String, id: String): RecordedEvent {
        val options = ReadStreamOptions.get()
            .backwards()
            .fromEnd()

        return client.readStream("$category-$id", options).get()
            .events
            .map { it.originalEvent }
            .first()
    }

    fun readAllEvents(): List<RecordedEvent> {
        val options = ReadAllOptions.get()
            .forwards()
            .fromStart()

        return client.readAll(options).get()
            .events
            .map { it.originalEvent }
    }
}
