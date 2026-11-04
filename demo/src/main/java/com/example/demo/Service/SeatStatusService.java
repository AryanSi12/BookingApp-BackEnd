package com.example.demo.Service;

import com.example.demo.Dto.SeatStatusDTO;
import com.example.demo.Models.SeatStatus;
import com.example.demo.Repository.SeatStatusRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SeatStatusService {
    public static String seatMapKey(ObjectId eventId) {
        return "seatMap:" + eventId;
    }

    @Autowired
    private RedisService redisService;

    @Autowired
    private SeatStatusRepository seatStatusRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    void ensureSeatMapIndex() {
        mongoTemplate.indexOps(SeatStatus.class)
                .ensureIndex(new Index().on("eventId.$id", Sort.Direction.ASC).named("seat_status_event"));
    }

    public void saveSeatsWithStatus(List<SeatStatus> seatStatusList) {
        try {
            seatStatusRepository.saveAll(seatStatusList);
            evictSeatMaps(seatStatusList);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteByEventId(ObjectId eventId) {
        seatStatusRepository.deleteByeventId(eventId);
        evictSeatMap(eventId);
    }

    public void evictSeatMap(ObjectId eventId) {
        if (eventId != null) {
            redisService.deleteKey(seatMapKey(eventId));
        }
    }

    public List<SeatStatusDTO> getSeatMap(ObjectId eventId) {
        String key = seatMapKey(eventId);
        List<SeatStatusDTO> seats = redisService.getSeats(key, new TypeReference<List<SeatStatusDTO>>() {});
        if (seats != null) {
            return seats;
        }
        Query query = new Query(Criteria.where("eventId.$id").is(eventId));
        query.fields().include("seatNumber").include("seatType").include("status");
        query.with(Sort.by(Sort.Direction.ASC, "seatNumber"));
        seats = mongoTemplate.find(query, SeatStatusDTO.class, "SeatStatus");
        redisService.setSeats(key, seats, 10);
        return seats;
    }

    public List<SeatStatus> getSeatStatusByEventId(ObjectId eventId) {
        try {
            String key = "allSeats" + eventId;
            List<SeatStatus> seats = redisService.getSeats(key, new TypeReference<List<SeatStatus>>() {});
            if (seats == null) {
                seats = seatStatusRepository.findByeventId(eventId);
                redisService.setSeats(key, seats, 10);
            }

            seats.sort(Comparator.comparing(SeatStatus::getSeatNumber));

            return seats;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void evictSeatMaps(List<SeatStatus> seatStatusList) {
        if (seatStatusList == null) {
            return;
        }
        Set<ObjectId> eventIds = new HashSet<>();
        for (SeatStatus status : seatStatusList) {
            if (status.getEventId() != null && status.getEventId().getEventId() != null) {
                eventIds.add(status.getEventId().getEventId());
            }
        }
        for (ObjectId eventId : eventIds) {
            evictSeatMap(eventId);
        }
    }

}
