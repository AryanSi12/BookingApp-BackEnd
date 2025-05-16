package com.example.demo.Controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.demo.Enums.Status;
import com.example.demo.Models.*;
import com.example.demo.Service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController

public class EventController {

    @Autowired
    private VenueService venueService;

    @Autowired
    private EventService eventService;

    @Autowired
    private SeatStatusService seatStatusService;

    @Autowired
    private UserService userService;

    @Autowired
    private Cloudinary cloudinary;

    @Autowired
    private BookingService bookingService;

    //Create an event (Only organizer has access)
    @Transactional
    @PostMapping(value = "/Organizer/createEvent/{venueId}",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<?> createEvent(
            @RequestPart("event") String eventJson,
            @RequestPart("image") MultipartFile image,
            @PathVariable ObjectId venueId
    ) {
        try {
            System.out.println(9);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule()); // Needed for LocalDateTime

            Event event = objectMapper.readValue(eventJson, Event.class);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);

            event.setOrganizerId(user);
            Venues eventVenue = venueService.getVenueById(venueId);
            event.setVenueId(eventVenue);

            // Upload image to Cloudinary
            Map<?, ?> uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
            String imageUrl = uploadResult.get("secure_url").toString();
            event.setEventImage(imageUrl);

            // Check for overlapping events
            List<Event> existingEvents = eventService.findEventByVenueId(eventVenue.getVenueId());
            for (Event existing : existingEvents) {
                if (event.getStartTime().isBefore(existing.getEndTime()) &&
                        event.getEndTime().isAfter(existing.getStartTime())) {
                    return new ResponseEntity<>("Time clash with another event", HttpStatus.BAD_REQUEST);
                }
            }

            // Save event and seat statuses
            Event savedEvent = eventService.saveEvent(event);
            List<SeatStatus> seatStatusList = new ArrayList<>();
            for (Seat seat : eventVenue.getSeats()) {
                SeatStatus status = new SeatStatus();
                status.setEventId(savedEvent);
                status.setSeatType(seat.getSeatType());
                status.setSeatNumber(seat.getSeatNumber());
                status.setStatus(Status.AVAILABLE);
                seatStatusList.add(status);
            }
            seatStatusService.saveSeatsWithStatus(seatStatusList);
            return new ResponseEntity<>(savedEvent, HttpStatus.CREATED);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Unable to create an event", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("User/getEventById/{eventId}")
    public ResponseEntity<?> getEventById(@PathVariable ObjectId eventId){
        try{
            Event event = eventService.getEventById(eventId);
            if(event == null)return new ResponseEntity<>("Some error occurred while fetching the event form db",
                    HttpStatus.BAD_REQUEST);
            return new ResponseEntity<>(event,HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Was not able to fetch the event",HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/Organizer/getAllEventsByUserId")
    public ResponseEntity<?> getAllEventsByUserId(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            return eventService.getAllEventsByUserId(user.getUserId());
        } catch (Exception e) {
            return new ResponseEntity<>("Error while fetching the events",HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/User/getAllEvents")
    public ResponseEntity<?> getAllEvents(){
        try{
            List<Event> events = eventService.getAllEvents();
            if(events == null)return new ResponseEntity<>("Some error occurred while fetching the events form db",
                    HttpStatus.BAD_REQUEST);
            return new ResponseEntity<>(events,HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Transactional
    @DeleteMapping("/Organizer/deleteEventById/{eventId}")
    public ResponseEntity<?> deleteEventById(@PathVariable ObjectId eventId){
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            Event event = eventService.getEventById(eventId);
            if(!user.getUserId().toString().equals(event.getOrganizerId().getUserId().toString())){
                return new ResponseEntity<> ("You are not authorized to delete this venue.",HttpStatus.FORBIDDEN);
            }
            seatStatusService.deleteByEventId(eventId);
            bookingService.deleteByEventId(eventId);
            eventService.deleteById(eventId);

            return new ResponseEntity<>("Event and associated seats deleted successfully.",HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error occurred while deleting an event",HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/User/getEventsByQuery")
    public ResponseEntity<?> getEventsByQuery(@RequestParam("query") String query){
        try {
            List<Event> events = eventService.getEventByQuery(query);
            return new ResponseEntity<>(events,HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Unable to fetch the seats",HttpStatus.BAD_REQUEST);
        }
    }

}
