package com.qualityminds.lazyval.integration;

import com.qualityminds.lazyval.LazyValue;

import java.time.LocalDate;

@LazyValue
public record OrderDate(LocalDate date) {
    public OrderDate {
        if(date.isAfter(LocalDate.now())){
            throw new IllegalArgumentException("Order date must not be in the future");
        }
    }

    public static OrderDate now(){
        return new OrderDate(LocalDate.now());
    }
}
