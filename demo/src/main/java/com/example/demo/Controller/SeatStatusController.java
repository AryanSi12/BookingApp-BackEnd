package com.example.demo.Controller;

import com.example.demo.Dto.SeatStatusDTO;
import com.example.demo.Models.SeatStatus;
import com.example.demo.Service.EventService;
import com.example.demo.Service.SeatStatusService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeatStatusController {
    @Autowired
    private SeatStatusService seatStatusService;

    @Autowired
    private EventService eventService;

    @GetMapping("User/getSeatStatusByEventId/{eventId}")
    public ResponseEntity<?> getSeatStatusByEventId(@PathVariable ObjectId eventId){
        try {
            List<SeatStatus> seatStatusList = seatStatusService.getSeatStatusByEventId(eventId);
            List<SeatStatusDTO> dtoList = seatStatusList.stream().map(seat ->
                    new SeatStatusDTO(seat.getSeatNumber(), seat.getSeatType(), seat.getStatus())
                    ).toList();
            return new ResponseEntity<>(dtoList, HttpStatus.OK);
        }
        catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Unable to fetch the status of the seats ",HttpStatus.BAD_REQUEST);
        }
    }
}
