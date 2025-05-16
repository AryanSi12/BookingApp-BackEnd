package com.example.demo.Repository;

import com.example.demo.Models.Event;
import com.example.demo.Models.Venues;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VenueRepository extends MongoRepository<Venues, ObjectId> {

    List<Venues> findAllBycreatedBy(ObjectId userId);
}
