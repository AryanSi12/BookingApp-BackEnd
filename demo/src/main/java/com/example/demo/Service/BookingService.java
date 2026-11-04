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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
        booking.setBookingStatus(Bookingstatus.PENDING);

        List<String> requested = booking.getBookedSeats();
        if (requested == null || requested.isEmpty()) {
            return new ResponseEntity<>("No seats selected for booking", HttpStatus.BAD_REQUEST);
        }

        System.out.println(booking);
        List<String> seatNumbers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String seatNumber : requested) {
            SeatStatus status = catalogStatus(seatStatuses, seatNumber);
            if (status == null || status.getStatus() == Status.BOOKED) {
                return new ResponseEntity<>("Seat " + seatNumber + " is already booked", HttpStatus.BAD_REQUEST);
            }
            if (seen.add(status.getSeatNumber().toUpperCase(Locale.ROOT))) {
                seatNumbers.add(status.getSeatNumber());
            }
        }
        booking.setBookedSeats(seatNumbers);

        if (venue == null || venue.getSeats() == null) {
            return new ResponseEntity<>("Failed to book the tickets", HttpStatus.BAD_REQUEST);
        }
        List<Seat> priced = new ArrayList<>();
        for (String seatNumber : seatNumbers) {
            Seat seat = venue.getSeats().stream()
                    .filter(candidate -> candidate != null
                            && candidate.getSeatNumber() != null
                            && candidate.getSeatNumber().equalsIgnoreCase(seatNumber))
                    .findFirst()
                    .orElse(null);
            if (seat == null) {
                return new ResponseEntity<>("Failed to book the tickets", HttpStatus.BAD_REQUEST);
            }
            priced.add(seat);
        }

        List<RedisService.SeatHold> holds = new ArrayList<>();
        boolean keepLocks = false;
        try {
            long amt = 0;
            for (int i = 0; i < seatNumbers.size(); i++) {
                String seatNumber = seatNumbers.get(i);
                Seat seat = priced.get(i);
                RedisService.SeatHold hold = redisService.lockSeat(event.getEventId(), seatNumber, user.getUserId(), 5);
                if (hold == null) {
                    return new ResponseEntity<>("Failed to book the tickets", HttpStatus.BAD_REQUEST);
                }
                holds.add(hold);
                if (seat.getSeatType() == SeatType.VIP) {
                    amt = amt + 10000;
                } else {
                    amt = amt + 5000;
                }
                messagingTemplate.convertAndSend("/topic/seats/" + eventId, "Seat " + hold.seatNumber() + " has been locked for booking.");
            }

            List<String> bookedSeats = new ArrayList<>();
            for (RedisService.SeatHold hold : holds) {
                bookedSeats.add(hold.seatNumber());
            }

            try {
                Stripe.apiKey = stripeApiKey;
                PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                        .setAmount(amt)
                        .setCurrency("inr")
                        .putMetadata("eventId", eventId.toHexString())
                        .putMetadata("userId", user.getUserId().toHexString())
                        .build();
                PaymentIntent paymentIntent = PaymentIntent.create(params);

                for (SeatStatus status : seatStatuses) {
                    if (seatNumbers.contains(status.getSeatNumber())) {
                        status.setStatus(Status.BOOKED);
                    }
                }
                seatStatusRepository.saveAll(seatStatuses);
                redisService.deleteKey(SeatStatusService.seatMapKey(eventId));
                redisService.deleteKey("allSeats" + eventId);

                booking.setBookingStatus(Bookingstatus.SUCCESSFULL);
                booking.setUserId(user);
                booking.setCreatedAt(LocalDateTime.now());
                booking.setAmountPaid(amt / 100);
                booking.setEventId(event);
                saveBooking(booking);

                messagingTemplate.convertAndSend("/topic/seats/" + eventId, "Booking successful. Seats: " + bookedSeats);
                keepLocks = true;
                return ResponseEntity.ok(java.util.Map.of(
                        "clientSecret", paymentIntent.getClientSecret(),
                        "amount", amt,
                        "seatsLocked", bookedSeats,
                        "bookingDetails", booking
                ));
            } catch (StripeException e) {
                messagingTemplate.convertAndSend("/topic/seats/" + eventId, "Payment creation failed. Please try again.");
                return new ResponseEntity<>("Payment creation failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } finally {
            if (!keepLocks) {
                releaseHolds(holds);
            }
        }
    }

    private SeatStatus catalogStatus(List<SeatStatus> seatStatuses, String seatNumber) {
        if (seatNumber == null || seatStatuses == null) {
            return null;
        }
        for (SeatStatus status : seatStatuses) {
            if (seatNumber.equals(status.getSeatNumber())) {
                return status;
            }
        }
        for (SeatStatus status : seatStatuses) {
            if (status.getSeatNumber() != null && status.getSeatNumber().equalsIgnoreCase(seatNumber)) {
                return status;
            }
        }
        return null;
    }

    private void releaseHolds(List<RedisService.SeatHold> holds) {
        for (RedisService.SeatHold hold : holds) {
            try {
                hold.release();
            } catch (RuntimeException ex) {
                // A Redis error here must not replace the booking response. The lock TTL still expires.
            }
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
