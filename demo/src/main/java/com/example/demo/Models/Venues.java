package com.example.demo.Models;

import com.example.demo.Configurations.ObjectIdSerializer;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Venues")
@ToString(exclude = "seats")

public class Venues {
    @Id
    @JsonSerialize(using = ObjectIdSerializer.class)
    private ObjectId venueId;

    private String venueName;

    private String venueLocation;

    private int rows;
    private int col;

    private String venueImage;

    @JsonManagedReference
    private List<Seat> seats;

    @DBRef
    private User createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

}
