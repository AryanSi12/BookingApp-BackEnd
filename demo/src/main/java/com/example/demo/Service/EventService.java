package com.example.demo.Service;

import com.example.demo.Models.Event;
import com.example.demo.Repository.EventsRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    @Autowired
        private RedisService redisService;

    @Autowired
    private EventsRepository eventsRepository;
    public Event saveEvent(Event event) {
        eventsRepository.save(event);
        redisService.deleteKey("allEvents");
        return event;
    }

    public Event getEventById(ObjectId eventId) {
        Optional<Event> event = eventsRepository.findById(eventId);
        return event.get();
    }

    public List<Event> getAllEvents() {
        List<Event> events = redisService.getEvents("allEvents", List.class);
        if (events != null) return events;

        events = eventsRepository.findAll();
        redisService.setEvents("allEvents", events, 10);
        return events;
    }

    public void deleteById(ObjectId eventId) {
        eventsRepository.deleteById(eventId);
    }

    public List<Event> getEventByQuery(String query) {
        return eventsRepository.findByTitleContainingIgnoreCase(query);
    }

    public List<Event> findEventByVenueId(ObjectId venueId) {
        return eventsRepository.findByvenueId(venueId);
    }

    public ResponseEntity<?> getAllEventsByUserId(ObjectId userId) {
        try{
            List<Event> events = eventsRepository.findAllByorganizerId(userId);
            return new ResponseEntity<>(events, HttpStatus.OK);
        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    public void deleteByVenueId(ObjectId venueId) {
        eventsRepository.deleteByvenueId(venueId);
    }
}
