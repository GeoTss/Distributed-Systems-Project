package com.example.client_efood.Domain;

import java.io.Serializable;

public class Location implements Serializable {

    private static final long serialVersionUID = 7L;

    private double latitude;
    private double longitude;
    public Location(
            double _latitude,
            double _longitude
    ) {
        this.latitude = _latitude;
        this.longitude = _longitude;
    }

    public double getDistanceTo(Location _otherLocation) {

//        if (!isValidLatitude(otherLatitude)) {
//            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
//        }
//        if (!isValidLongitude(otherLongitude)) {
//            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
//        }

        if(!isValidLocation(_otherLocation)){
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees and Longitude must be between -180 and 180 degrees.");
        }

        final int earthRadiusKm = 6371; // Radius of earth in Km
        // Degrees to radians
        final double degreesToRadians = Math.PI / 180.0;

        double otherLatitude = _otherLocation.getLatitude();
        double otherLongitude = _otherLocation.getLongitude();

        double lat1Rad = this.latitude * degreesToRadians;
        double lon1Rad = this.longitude * degreesToRadians;
        double lat2Rad = otherLatitude * degreesToRadians;
        double lon2Rad = otherLongitude * degreesToRadians;
        // Calculate the differences between the latitudes and longitudes
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;
        // Haversine formula
        double a = Math.pow(Math.sin(deltaLat / 2), 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.pow(Math.sin(deltaLon / 2), 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return earthRadiusKm * c;
    }

    public boolean isInRange(Location _otherLoc, float _max_distance){
        return getDistanceTo(_otherLoc) <= _max_distance;
    }

    private boolean isValidLatitude(double _latitude) {
        return _latitude >= -90 && _latitude <= 90;
    }
    private boolean isValidLongitude(double _longitude) {
        return _longitude >= -180 && _longitude <= 180;
    }

    private boolean isValidLocation(Location _location){
        return isValidLatitude(_location.latitude) && isValidLongitude(_location.longitude);
    }

    public double getLatitude() {
        return this.latitude;
    }
    public double getLongitude() {
        return this.longitude;
    }
    public void setLocation(double _latitude, double _longitude) {
        this.latitude = _latitude;
        this.longitude = _longitude;
    }

    @Override
    public String toString(){
        return "Location: [Latitude: " + getLatitude() + ", Longitude: " + getLongitude() + "]";
    }
}
