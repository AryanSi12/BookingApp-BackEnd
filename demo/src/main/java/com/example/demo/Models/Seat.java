package com.example.demo.Models;

import com.example.demo.Configurations.ObjectIdSerializer;
import com.example.demo.Enums.SeatType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "seat")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "venueId")

public class Seat {
    @Id
    @JsonSerialize(using = ObjectIdSerializer.class)
    private ObjectId seatId;

    private String seatNumber;

    private SeatType seatType;

    private Number row;

    private Number col;

    @DBRef
    @JsonBackReference
    private Venues venueId;


}
