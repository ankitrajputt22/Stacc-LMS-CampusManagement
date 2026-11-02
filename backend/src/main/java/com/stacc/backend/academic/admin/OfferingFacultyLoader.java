package com.stacc.backend.academic.admin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingFaculty;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import org.springframework.stereotype.Component;

/**
 * Who teaches a set of offerings, for admin lists: every teaching assignment with the faculty member's login ID and,
 * where a person profile exists, their name. Two queries however many offerings are listed.
 */
@Component
public class OfferingFacultyLoader {

    private final TeachingAssignmentRepository assignments;
    private final PersonProfileRepository people;

    OfferingFacultyLoader(TeachingAssignmentRepository assignments, PersonProfileRepository people) {
        this.assignments = assignments;
        this.people = people;
    }

    /** Each offering's assignments, active ones first, keyed by offering ID; an offering with none is missing. */
    public Map<Long, List<OfferingFaculty>> byOffering(Collection<Long> courseOfferingIds) {
        if (courseOfferingIds.isEmpty()) {
            return Map.of();
        }
        List<TeachingAssignment> found = assignments.findAllWithFacultyByCourseOfferingIdIn(courseOfferingIds);
        Map<Long, String> names = names(found);
        return found.stream().collect(Collectors.groupingBy(
                assignment -> assignment.getCourseOffering().getId(),
                LinkedHashMap::new,
                Collectors.mapping(assignment -> OfferingFaculty.of(assignment,
                        names.get(assignment.getFacultyProfile().getUserAccount().getId())), Collectors.toList())));
    }

    List<OfferingFaculty> forOffering(Long courseOfferingId) {
        return byOffering(List.of(courseOfferingId)).getOrDefault(courseOfferingId, List.of());
    }

    OfferingFaculty describe(TeachingAssignment assignment) {
        return OfferingFaculty.of(assignment, names(List.of(assignment))
                .get(assignment.getFacultyProfile().getUserAccount().getId()));
    }

    /** The display name of each account that has a person profile. */
    Map<Long, String> namesOfAccounts(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return people.findAllByUserAccountIdIn(accountIds).stream()
                .collect(Collectors.toMap(person -> person.getUserAccount().getId(), PersonProfile::displayName,
                        (first, second) -> first));
    }

    private Map<Long, String> names(List<TeachingAssignment> found) {
        return namesOfAccounts(found.stream().map(assignment -> assignment.getFacultyProfile().getUserAccount().getId())
                .collect(Collectors.toSet()));
    }
}
