package com.example.demo.Controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.example.demo.Enums.SeatType;
import com.example.demo.Models.Seat;
import com.example.demo.Models.User;
import com.example.demo.Models.Venues;
import com.example.demo.Service.RedisService;
import com.example.demo.Service.SeatService;
import com.example.demo.Service.UserService;
import com.example.demo.Service.VenueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class VenueController {

    @Autowired
    private VenueService venueService;

    @Autowired
    private UserService userService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private Cloudinary cloudinary;

    @Autowired
    private RedisService redisService;

    //Creating a Venue
    @Transactional
    @PostMapping(
            value = "/Organizer/createVenue",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> createVenue(
            @RequestPart("venue") String venueJson,
            @RequestPart("image") MultipartFile image
    ) {
        try {
            // Deserialize the JSON string to a Venue object
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            Venues venue = objectMapper.readValue(venueJson, Venues.class);

            // Get the user by userId
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            venue.setCreatedBy(user);

            // Upload the image to Cloudinary or similar storage service
            Map<?, ?> uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
            String imageUrl = uploadResult.get("secure_url").toString();
            venue.setVenueImage(imageUrl); // Assuming you have a venueImage field in the Venues model

            // Create the venue
            Venues savedVenue = venueService.createVenue(venue);
            if (savedVenue == null) {
                return new ResponseEntity<>("Unable to create venue", HttpStatus.BAD_REQUEST);
            }

            // Create the seats for the venue
            int rows = venue.getRows();
            int cols = venue.getCol();
            List<Seat> seats = new ArrayList<>();
            for (int i = 0; i < rows; i++) {
                char rowLetter = (char) ('A' + i);
                for (int j = 1; j <= cols; j++) {
                    String seatNumber = rowLetter + String.valueOf(j);
                    SeatType type = (i == 0) ? SeatType.VIP : SeatType.REGULAR;

                    Seat seat = new Seat();
                    seat.setSeatNumber(seatNumber);
                    seat.setVenueId(savedVenue);
                    seat.setSeatType(type);
                    seat.setRow(i);
                    seat.setCol(j);
                    seats.add(seat);
                }
            }
            seatService.setAllSeats(seats);

            // Set the seats in the saved venue
            savedVenue.setSeats(seats);
            venueService.createVenue(savedVenue); // Save the venue with the seats

            return new ResponseEntity<>(savedVenue, HttpStatus.OK);
            //return new ResponseEntity<>(savedVenue, HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Exception occurred while creating a venue", HttpStatus.BAD_REQUEST);
        }
    }

    //Fetching all the venues
    @GetMapping("/User/getAllVenues")
    public ResponseEntity<?> getAllVenues() {
        try {
            List<Venues> venues = venueService.getAllVenues();
            System.out.println("Fetched venues: " + venues);

            return new ResponseEntity<>(venues, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace(); // Helpful debug
            return new ResponseEntity<>("Unable to fetch all the venues", HttpStatus.BAD_REQUEST);
        }
    }

    //Fetching the detail of a venue by its id
    @GetMapping("/User/getVenueById/{venueId}")
    public ResponseEntity<?> getVenueById(@PathVariable ObjectId venueId){
        try{
            Venues venue = venueService.getVenueById(venueId);
            if(venue != null){Map<String, Object> response = new HashMap<>();

                return new ResponseEntity<>(venue, HttpStatus.OK);
            }
            return new ResponseEntity<>("Something went wrong while fetching the venue by id through database"
            ,HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("Some Error occurred while fetching the venue",HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/Organizer/getAllVenuesByUserId")
    public ResponseEntity<?> getAllVenuesByUserId(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            return venueService.getAllVenuesByUserId(user.getUserId());
        } catch (Exception e) {
            return new ResponseEntity<>("Error while fetching the events",HttpStatus.BAD_REQUEST);
        }
    }

    //Delete a Venue By Id
    @DeleteMapping("/Organizer/deleteVenueById/{venueId}")
    public ResponseEntity<?> deleteVenueById(@PathVariable ObjectId venueId){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            Venues venue = venueService.getVenueById(venueId);
            if(!user.getUserId().toString().equals(venue.getCreatedBy().getUserId().toString())){
                System.out.println(user.getUserId()+"  "+venue.getCreatedBy().getUserId());
                return new ResponseEntity<> ("You are not authorized to delete this venue.",HttpStatus.FORBIDDEN);
            }

            seatService.deleteByVenueId(venueId);
            redisService.deleteKey("allEvents");
            venueService.deleteVenueById(venueId);

            return new ResponseEntity<>("Venue and associated seats deleted successfully.",HttpStatus.OK);


        } catch (Exception e) {
            return new ResponseEntity<>("Error while deleting the venue",HttpStatus.BAD_REQUEST);
        }
    }


}
