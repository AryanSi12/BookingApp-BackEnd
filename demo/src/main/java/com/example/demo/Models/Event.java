package com.example.demo.Models;

import com.example.demo.Configurations.ObjectIdSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "Event")
public class Event {
    @Id
    @JsonSerialize(using = ObjectIdSerializer.class)
    private ObjectId eventId;

    private String title;
    private String aboutEvent;

    private String eventImage;

    @DBRef
    private Venues venueId;

    @DBRef
    private User organizerId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
