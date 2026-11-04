package com.example.demo.Service;

import com.example.demo.Models.Event;
import com.example.demo.Models.User;
import com.example.demo.Models.Venues;
import com.mongodb.DBRef;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogReadService {
    private final MongoTemplate mongoTemplate;

    public CatalogReadService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Event> eventList() {
        List<EventRow> rows = mongoTemplate.find(new Query(), EventRow.class, "Event");
        Set<ObjectId> venueIds = new HashSet<>();
        Set<ObjectId> userIds = new HashSet<>();
        for (EventRow row : rows) {
            addRef(venueIds, row.venueId);
            addRef(userIds, row.organizerId);
        }
        Map<ObjectId, Venues> venues = venuesById(venueIds, true);
        Map<ObjectId, User> users = usersById(userIds);
        List<Event> events = new ArrayList<>();
        for (EventRow row : rows) {
            Event event = new Event();
            event.setEventId(row.eventId);
            event.setTitle(row.title);
            event.setAboutEvent(row.aboutEvent);
            event.setEventImage(row.eventImage);
            event.setStartTime(row.startTime);
            event.setEndTime(row.endTime);
            event.setVenueId(venues.get(refId(row.venueId)));
            event.setOrganizerId(users.get(refId(row.organizerId)));
            events.add(event);
        }
        return events;
    }

    public List<Venues> venueList() {
        List<VenueRow> rows = mongoTemplate.find(new Query(), VenueRow.class, "Venues");
        Set<ObjectId> userIds = new HashSet<>();
        for (VenueRow row : rows) {
            addRef(userIds, row.createdBy);
        }
        Map<ObjectId, User> users = usersById(userIds);
        List<Venues> venues = new ArrayList<>();
        for (VenueRow row : rows) {
            Venues venue = new Venues();
            venue.setVenueId(row.venueId);
            venue.setVenueName(row.venueName);
            venue.setVenueLocation(row.venueLocation);
            venue.setRows(row.rows);
            venue.setCol(row.col);
            venue.setVenueImage(row.venueImage);
            venue.setCreatedAt(row.createdAt);
            venue.setUpdatedAt(row.updatedAt);
            venue.setCreatedBy(users.get(refId(row.createdBy)));
            venues.add(venue);
        }
        return venues;
    }

    private Map<ObjectId, Venues> venuesById(Collection<ObjectId> ids, boolean excludeOwner) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().exclude("seats");
        if (excludeOwner) {
            query.fields().exclude("createdBy");
        }
        Map<ObjectId, Venues> byId = new HashMap<>();
        for (Venues venue : mongoTemplate.find(query, Venues.class)) {
            byId.put(venue.getVenueId(), venue);
        }
        return byId;
    }

    private Map<ObjectId, User> usersById(Collection<ObjectId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().exclude("password");
        Map<ObjectId, User> byId = new HashMap<>();
        for (User user : mongoTemplate.find(query, User.class)) {
            byId.put(user.getUserId(), user);
        }
        return byId;
    }

    private static void addRef(Set<ObjectId> ids, DBRef ref) {
        ObjectId id = refId(ref);
        if (id != null) {
            ids.add(id);
        }
    }

    private static ObjectId refId(DBRef ref) {
        if (ref == null || !(ref.getId() instanceof ObjectId objectId)) {
            return null;
        }
        return objectId;
    }

    @Data
    @NoArgsConstructor
    public static class EventRow {
        @Id
        private ObjectId eventId;
        private String title;
        private String aboutEvent;
        private String eventImage;
        private DBRef venueId;
        private DBRef organizerId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    @NoArgsConstructor
    public static class VenueRow {
        @Id
        private ObjectId venueId;
        private String venueName;
        private String venueLocation;
        private int rows;
        private int col;
        private String venueImage;
        private DBRef createdBy;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
