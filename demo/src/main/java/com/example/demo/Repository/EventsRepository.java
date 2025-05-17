package com.example.demo.Repository;

import com.example.demo.Models.Event;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;


public interface EventsRepository extends MongoRepository<Event, ObjectId> {
    @Query("{'title': { $regex: ?0, $options: 'i' }}")
    List<Event> findByTitleContainingIgnoreCase(String query);
    List<Event> findByvenueId(ObjectId venueId);

    List<Event> findByendTimeBefore(LocalDateTime now);

    List<Event> findAllByorganizerId(ObjectId userId);

    void deleteByvenueId(ObjectId venueId);
}
