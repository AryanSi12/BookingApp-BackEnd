package com.example.demo.Repository;

import com.example.demo.Models.Seat;
import com.example.demo.Models.Venues;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeatRepository extends MongoRepository<Seat , ObjectId> {
    void deleteByvenueId(ObjectId venueId);
}
