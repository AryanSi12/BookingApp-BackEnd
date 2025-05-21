package com.example.demo.Models;

import com.example.demo.Configurations.ObjectIdSerializer;
import com.example.demo.Enums.SeatType;
import com.example.demo.Enums.Status;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "SeatStatus")

public class SeatStatus {
    @Id
    @JsonSerialize(using = ObjectIdSerializer.class)
    private ObjectId seatStatusId;
    private String seatNumber;

    private SeatType seatType;

    private Status status;

    @DBRef
    private User lockedBy;

    private LocalDateTime lockedAt;

    @DBRef
    private Event eventId;
}
