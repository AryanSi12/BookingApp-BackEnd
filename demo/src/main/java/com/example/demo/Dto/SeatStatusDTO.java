package com.example.demo.Dto;

import com.example.demo.Enums.SeatType;
import com.example.demo.Enums.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class SeatStatusDTO {
    private String seatNumber;
    private SeatType seatType;
    private Status status;

    public SeatStatusDTO(String seatNumber, SeatType seatType, Status status) {
        this.seatNumber = seatNumber;
        this.seatType = seatType;
        this.status = status;
    }

}
