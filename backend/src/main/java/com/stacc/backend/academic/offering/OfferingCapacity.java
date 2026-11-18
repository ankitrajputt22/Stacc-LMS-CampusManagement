package com.stacc.backend.academic.offering;

public final class OfferingCapacity {

    private OfferingCapacity() {
    }

    public static String fullMessage(CourseOffering offering) {
        Integer capacity = offering.getCapacity();
        String name = offering.getCourse().getCode()
                + (offering.getSection() == null ? "" : " section " + offering.getSection().getCode());
        return name + " is full: " + (capacity != null && capacity == 1
                ? "its one place is taken." : "all " + capacity + " places are taken.");
    }
}
