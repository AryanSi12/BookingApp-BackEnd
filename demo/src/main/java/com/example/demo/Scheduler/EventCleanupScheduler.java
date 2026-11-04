package com.example.demo.Scheduler;

import com.example.demo.Models.Event;
import com.example.demo.Repository.EventsRepository;
import com.example.demo.Service.EventService;
import com.example.demo.Service.SeatStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class EventCleanupScheduler {
    @Autowired
    private EventsRepository eventRepository;

    @Autowired
    private SeatStatusService seatStatusService;

    @Autowired
    private EventService eventService;

    // Runs every hour
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void deleteExpiredEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> expiredEvents = eventRepository.findByendTimeBefore(now);

        for (Event event : expiredEvents) {
            try {
                seatStatusService.deleteByEventId(event.getEventId());
                eventService.deleteById(event.getEventId());
                System.out.println("Deleted expired event: " + event.getTitle());
            } catch (Exception e) {
                System.err.println("Failed to delete event: " + event.getTitle());
                e.printStackTrace();
            }
        }
    }
}
