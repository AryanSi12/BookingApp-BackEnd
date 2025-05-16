package com.example.demo.Service;

import com.example.demo.Models.Seat;
import com.example.demo.Models.Venues;
import com.example.demo.Repository.SeatRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatService {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private MongoTemplate mongoTemplate;
    public void setAllSeats(List<Seat> seats) {
        seatRepository.saveAll(seats);

    }

    public Seat getSeatBySeatNumber(String seatNumber, Venues venue){
        try {
            System.out.println("ok");
            Query query = new Query();
            query.addCriteria(Criteria.where("seatNumber").is(seatNumber).and("venue").is(venue));
            return mongoTemplate.findOne(query,Seat.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }


    }

    public void deleteByVenueId(ObjectId venueId) {
        seatRepository.deleteByvenueId(venueId);
    }
}
