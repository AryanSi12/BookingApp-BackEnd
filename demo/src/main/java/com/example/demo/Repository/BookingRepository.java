package com.example.demo.Repository;

import com.example.demo.Models.Booking;
import com.example.demo.Models.Event;
import com.example.demo.Models.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BookingRepository extends MongoRepository<Booking, ObjectId> {
    List<Booking> findByuserId(User userId);

    List<Booking> findAllByeventId(Event event);

    void deleteByeventId(ObjectId eventId);
}
