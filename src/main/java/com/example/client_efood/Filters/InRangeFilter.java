package com.example.client_efood.Filters;

import com.example.client_efood.Domain.Location;

public class InRangeFilter<T extends Locatable> implements Filter{
    double maximum_distance;
    Location current_location;

    public InRangeFilter(Location current_location, double maximum_distance){
        this.current_location = current_location;
        this.maximum_distance = maximum_distance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean satisfy(Object obj) {
        return ((T) obj).getLocation().getDistanceTo(current_location) <= maximum_distance;
    }
}
