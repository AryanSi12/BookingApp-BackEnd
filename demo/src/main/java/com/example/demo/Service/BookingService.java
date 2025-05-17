package com.example.demo.Service;

import com.example.demo.Enums.Bookingstatus;
import com.example.demo.Enums.SeatType;
import com.example.demo.Enums.Status;
import com.example.demo.Models.*;
import com.example.demo.Repository.BookingRepository;
import com.example.demo.Repository.SeatStatusRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private UserService userService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private SeatStatusService seatStatusService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SeatStatusRepository seatStatusRepository;
    @Value("${stripe.api.key}")
    private String stripeApiKey;

    public Booking saveBooking(Booking booking){
        return bookingRepository.save(booking);
    }

    @Transactional
    public ResponseEntity<?> bookSeat(Booking booking, ObjectId eventId, List<SeatStatus> seatStatuses) {
        Event event = eventService.getEventById(eventId);
        Venues venue = event.getVenueId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userService.getCurrUser(username);
        List<String> bookedSeats = new ArrayList<>();
        //set booking status as pending
        booking.setBookingStatus(Bookingstatus.PENDING);

        List<String> seatNumbers = booking.getBookedSeats();
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return new ResponseEntity<>("No seats selected for booking", HttpStatus.BAD_REQUEST);
        }

        System.out.println(booking);
        for (String seatNumber : seatNumbers) {
            SeatStatus status = seatStatuses.stream()
                    .filter(ss -> ss.getSeatNumber().equalsIgnoreCase(seatNumber))
                    .findFirst()
                    .orElse(null);

            if (status == null || status.getStatus() == Status.BOOKED) {
                return new ResponseEntity<>("Seat " + seatNumber + " is already booked", HttpStatus.BAD_REQUEST);
            }
        }

        long amt = 0;
        //fetch details of the seats and lock them
        for (String seatNumber : seatNumbers){
            //System.out.println(seatNumber);
            Boolean isLocked = redisService.lockSeat(event.getEventId(),seatNumber,user.getUserId(),5);
            //fetch seat details
            Seat seat = venue.getSeats().stream()
                    .filter(seat1 -> seat1.getSeatNumber().equalsIgnoreCase(seatNumber))
                    .findFirst()
                    .orElse(null);

            if(seat.getSeatType() == SeatType.VIP)amt=amt+10000;
            else amt=amt+5000;
            bookedSeats.add(seatNumber);
            //unlock all the seats that were previously locked
            if(Boolean.FALSE.equals(isLocked)){
                for (String seatNo : bookedSeats) {
                    redisService.unlockSeat(event.getEventId(), seatNo);
                }
                return new ResponseEntity<>("Failed to book the tickets", HttpStatus.BAD_REQUEST);
            }
            messagingTemplate.convertAndSend("/topic/seats/" + eventId, "Seat " + seatNumber + " has been locked for booking.");
        }


        //Payment gateway using Stripe
        try {
            Stripe.apiKey = stripeApiKey;
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amt)
                    .setCurrency("inr")
                    .putMetadata("eventId", eventId.toHexString())
                    .putMetadata("userId", user.getUserId().toHexString())
                    .build();
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            //setting seat status to booked
            List<SeatStatus> updatedStatuses = new ArrayList<>();

            for (SeatStatus status : seatStatuses) {
                if (seatNumbers.contains(status.getSeatNumber())) {
                    status.setStatus(Status.BOOKED);
                }
            }
// Save updated seatStatuses directly
            seatStatusRepository.saveAll(seatStatuses);


            booking.setBookingStatus(Bookingstatus.SUCCESSFULL);
            booking.setUserId(user);
            booking.setCreatedAt(LocalDateTime.now());
            booking.setAmountPaid(amt/100);
            booking.setEventId(event);
            Booking Booked = saveBooking(booking);

            messagingTemplate.convertAndSend("/topic/seats/" + eventId, "Booking successful. Seats: " + bookedSeats);

            return ResponseEntity.ok(java.util.Map.of(
                    "clientSecret", paymentIntent.getClientSecret(),
                    "amount", amt,
                    "seatsLocked", bookedSeats,
                    "bookingDetails" , booking
            ));
        } catch (StripeException e) {
            // Unlock all the seats if Stripe fails
            for (String seatNo : bookedSeats) {
                redisService.unlockSeat(event.getEventId(), seatNo);
            }
            // Notify all clients that the payment creation failed
            messagingTemplate.convertAndSend("/topic/seats/" + eventId, "Payment creation failed. Please try again.");

            return new ResponseEntity<>("Payment creation failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public List<Booking> fetchBookingByUserId(User userId) {
        return bookingRepository.findByuserId(userId);
    }

    public List<Booking> fetchBookingsByEvent(Event event) {
        return bookingRepository.findAllByeventId(event);
    }

    public ResponseEntity<?> fetchByBookingId(ObjectId bookingId) {
        try{
            Optional<Booking> booking = bookingRepository.findById(bookingId);
            if(booking.isPresent()) return new ResponseEntity<>(booking.get(),HttpStatus.OK);
            return new ResponseEntity<>("No ticket with this id",HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("Error while fetching from db",HttpStatus.BAD_REQUEST);
        }
    }

    public void deleteByEventId(ObjectId eventId) {
        bookingRepository.deleteByeventId(eventId);
    }
}
