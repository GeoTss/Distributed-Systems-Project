package com.example.client_efood.Filters;

public class RateFilter<T extends Rateable> implements Filter {

    float _min_rating;

    public RateFilter(float min_rating) {
        _min_rating = min_rating;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean satisfy(Object obj) {
        return ((T) obj).getRating() >= _min_rating;
    }

}
