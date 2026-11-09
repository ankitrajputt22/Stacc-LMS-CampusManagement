package com.stacc.backend.engagement.events;

import java.util.Optional;

import com.stacc.backend.approval.ApproverRelationship;
import com.stacc.backend.approval.ApproverRelationshipResolver;
import com.stacc.backend.engagement.groups.CampusGroupService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The faculty advisor of the group a campus event belongs to; nothing for any other request. */
@Component
public class GroupAdvisorApprover implements ApproverRelationshipResolver {

    private final CampusEventRepository events;
    private final CampusGroupService groups;

    public GroupAdvisorApprover(CampusEventRepository events, CampusGroupService groups) {
        this.events = events;
        this.groups = groups;
    }

    @Override
    public ApproverRelationship relationship() {
        return ApproverRelationship.GROUP_ADVISOR;
    }

    @Override
    public Optional<Long> approverFor(Long requesterAccountId) {
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> approverFor(Long requesterAccountId, String sourceType, String sourceId) {
        if (!CampusEventService.SOURCE_TYPE.equals(sourceType) || sourceId == null || !sourceId.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        return events.findById(Long.valueOf(sourceId)).filter(event -> event.getGroup() != null)
                .flatMap(event -> groups.advisorOf(event.getGroup().getId()));
    }
}
