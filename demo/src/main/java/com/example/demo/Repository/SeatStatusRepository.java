package com.example.demo.Repository;

import com.example.demo.Models.SeatStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SeatStatusRepository extends MongoRepository<SeatStatus , ObjectId> {
    void deleteByeventId(ObjectId eventId);
    List<SeatStatus> findByeventId(ObjectId eventId);

}
