package com.example.demo.Controller;

import com.example.demo.Models.Booking;
import com.example.demo.Models.Event;
import com.example.demo.Models.SeatStatus;
import com.example.demo.Models.User;
import com.example.demo.Service.BookingService;
import com.example.demo.Service.EventService;
import com.example.demo.Service.SeatStatusService;
import com.example.demo.Service.UserService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserService userService;

    @Autowired
    private EventService eventService;

    @Autowired
    private SeatStatusService seatStatusService;

    @Transactional
    @PostMapping("/User/bookSeat/{eventId}")
    public ResponseEntity<?> bookSeat(@RequestBody Booking booking, @PathVariable ObjectId eventId){
        try{
            System.out.println(booking);
            List<SeatStatus> seatStatuses = seatStatusService.getSeatStatusByEventId(eventId);
            return bookingService.bookSeat(booking,eventId,seatStatuses);
        } catch (Exception e) {
            return new ResponseEntity<>("Error occurred while booking a seat", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/User/fetchBookingByUserId")
    public ResponseEntity<?> fetchBookingByUserId(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);

            List<Booking> booking = bookingService.fetchBookingByUserId(user);
            return new ResponseEntity<>(booking,HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Unable to fetch the booking",HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/Organizer/fetchAllBookingsByEventId/{eventId}")
    public ResponseEntity<?> fetchAllBookingsByEventId(@PathVariable ObjectId eventId){
        try{
            Event event = eventService.getEventById(eventId);
            List<Booking> bookings = bookingService.fetchBookingsByEvent(event);
            return new ResponseEntity<>(bookings,HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/User/fetchBookingByBookingId/{bookingId}")
    public ResponseEntity<?> fetchBookingByBookingId(@PathVariable ObjectId bookingId){
        try{
            return bookingService.fetchByBookingId(bookingId);
        } catch (Exception e) {
            return new ResponseEntity<>("Unable to fetch the booking",HttpStatus.BAD_REQUEST);
        }
    }

}
