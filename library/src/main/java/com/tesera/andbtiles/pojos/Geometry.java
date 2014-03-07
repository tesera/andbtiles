package com.tesera.andbtiles.pojos;

import java.util.ArrayList;
import java.util.List;

public class Geometry {

    private List<Double> coordinates = new ArrayList<>();
    private String type;

    public List<Double> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Double> coordinates) {
        this.coordinates = coordinates;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
