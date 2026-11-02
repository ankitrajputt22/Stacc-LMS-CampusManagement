package com.stacc.backend.academic.offering;

/** The plain message when a course offering has no free place, shared by admin enrollment and self-registration. */
public final class OfferingCapacity {

    private OfferingCapacity() {
    }

    /** "CS301 section A is full: all 60 places are taken." */
    public static String fullMessage(CourseOffering offering) {
        Integer capacity = offering.getCapacity();
        String name = offering.getCourse().getCode()
                + (offering.getSection() == null ? "" : " section " + offering.getSection().getCode());
        return name + " is full: " + (capacity != null && capacity == 1
                ? "its one place is taken." : "all " + capacity + " places are taken.");
    }
}
