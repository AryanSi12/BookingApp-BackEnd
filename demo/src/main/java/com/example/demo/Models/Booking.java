package com.example.demo.Models;

import com.example.demo.Configurations.ObjectIdSerializer;
import com.example.demo.Enums.Bookingstatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    @Id
    @JsonSerialize(using = ObjectIdSerializer.class)
    private ObjectId bookingId;

    @DBRef
    private Event eventId;

    @DBRef
    private User userId;

    private List<String> bookedSeats;

    private Number amountPaid;

    private Bookingstatus bookingStatus;

    private LocalDateTime createdAt;
}
