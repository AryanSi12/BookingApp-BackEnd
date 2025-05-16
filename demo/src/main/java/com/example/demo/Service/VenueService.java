package com.example.demo.Service;

import com.example.demo.Models.Event;
import com.example.demo.Models.Venues;
import com.example.demo.Repository.VenueRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VenueService {
    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private RedisService redisService;
    public Venues createVenue(Venues venue) {
        try {
            Venues savedVenue = venueRepository.save(venue);  // Save the venue
            redisService.deleteKey("allVenues");
            return savedVenue;  // Return true if the venue was successfully saved
        } catch (Exception e) {
           e.printStackTrace();
        }
        return null;
    }


    public List<Venues> getAllVenues() {
        List<Venues> venues = redisService.getVenues("allVenues",List.class);
        if(venues != null)return venues;
        venues = venueRepository.findAll();
        redisService.setVenues("allVenues",venues,10);
        return venues;
    }

    public Venues getVenueById(ObjectId venueId) {
        Optional<Venues> venuesOptional = venueRepository.findById(venueId);
        return venuesOptional.get();
    }

    public void deleteVenueById(ObjectId venueId) {
        venueRepository.deleteById(venueId);
    }

    public ResponseEntity<?> getAllVenuesByUserId(ObjectId userId) {
        try{
            List<Venues> venues = venueRepository.findAllBycreatedBy(userId);
            return new ResponseEntity<>(venues, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
