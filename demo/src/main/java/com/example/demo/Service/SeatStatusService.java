package com.example.demo.Service;

import com.example.demo.Models.SeatStatus;
import com.example.demo.Repository.SeatStatusRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatStatusService {

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
        return seatStatusRepository.findByeventId(eventId);
    }
}
