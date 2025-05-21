package com.example.demo.Service;

import com.example.demo.Models.SeatStatus;
import com.example.demo.Repository.SeatStatusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatStatusService {
    @Autowired
    private RedisService redisService;

    @Autowired
    private SeatStatusRepository seatStatusRepository;
    public void saveSeatsWithStatus(List<SeatStatus> seatStatusList) {
        try {
            seatStatusRepository.saveAll(seatStatusList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteByEventId(ObjectId eventId) {
        seatStatusRepository.deleteByeventId(eventId);
    }

    public List<SeatStatus> getSeatStatusByEventId(ObjectId eventId) {
        try {
            String key = "allSeats"+eventId;
            List<SeatStatus> seats = redisService.getSeats(key, new TypeReference<List<SeatStatus>>() {});
            if(seats!=null)return seats;
            seats = seatStatusRepository.findByeventId(eventId);
            redisService.setSeats(key,seats,10);
            return seats;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }
}
