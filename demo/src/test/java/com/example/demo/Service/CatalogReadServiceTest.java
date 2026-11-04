package com.example.demo.Service;

import com.example.demo.Models.Event;
import com.example.demo.Models.Venues;
import com.mongodb.DBRef;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogReadServiceTest {

    @Test
    void eventListKeepsRowsWhenEveryReferenceIsMissing() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        CatalogReadService.EventRow row = new CatalogReadService.EventRow();
        row.setEventId(new ObjectId());
        row.setTitle("Show");
        row.setVenueId(null);
        row.setOrganizerId(new DBRef("User", "not-an-object-id"));
        when(mongo.find(any(Query.class), eq(CatalogReadService.EventRow.class), eq("Event")))
                .thenReturn(List.of(row));

        List<Event> events = new CatalogReadService(mongo).eventList();

        assertEquals(1, events.size());
        assertEquals("Show", events.get(0).getTitle());
        assertNull(events.get(0).getVenueId());
        assertNull(events.get(0).getOrganizerId());
    }

    @Test
    void venueListKeepsRowsWhenTheOwnerReferenceIsMissing() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        CatalogReadService.VenueRow row = new CatalogReadService.VenueRow();
        row.setVenueId(new ObjectId());
        row.setVenueName("Hall");
        row.setCreatedBy(null);
        when(mongo.find(any(Query.class), eq(CatalogReadService.VenueRow.class), eq("Venues")))
                .thenReturn(List.of(row));

        List<Venues> venues = new CatalogReadService(mongo).venueList();

        assertEquals(1, venues.size());
        assertEquals("Hall", venues.get(0).getVenueName());
        assertNull(venues.get(0).getCreatedBy());
    }
}
