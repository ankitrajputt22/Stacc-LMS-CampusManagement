package com.stacc.backend.hostel.outing;

import java.util.Collection;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelAllocation;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelRoom;
import com.stacc.backend.support.helpdesk.HelpdeskActor;
import com.stacc.backend.support.helpdesk.HelpdeskCategoryRepository;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskTicketRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketDetail;
import com.stacc.backend.support.helpdesk.HelpdeskTicketCommandService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A resident's room complaint is an ordinary helpdesk ticket of the HOSTEL category (routed to its team), raised as the
 * student with their current room written at the top. There is no second ticket engine; the helpdesk does the rest.
 */
@Service
@Transactional
public class HostelComplaintService {

    public static final String CATEGORY_CODE = "HOSTEL";

    private final HostelAllocationRepository allocations;
    private final HelpdeskCategoryRepository categories;
    private final HelpdeskTicketCommandService tickets;

    public HostelComplaintService(HostelAllocationRepository allocations, HelpdeskCategoryRepository categories,
            HelpdeskTicketCommandService tickets) {
        this.allocations = allocations;
        this.categories = categories;
        this.tickets = tickets;
    }

    public HelpdeskTicketDetail complain(Long accountId, Collection<? extends GrantedAuthority> authorities, String subject,
            String description, HelpdeskTicketPriority priority) {
        HostelAllocation allocation = allocations.findActiveForStudentAccount(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Only students who currently live in a hostel can report a room problem."));
        Long categoryId = categories.findByCode(CATEGORY_CODE).filter(category -> category.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Hostel complaints are not open right now. Use Support instead.")).getId();
        HostelRoom room = allocation.getBed().getRoom();
        String where = "Room: " + room.getHostel().getName() + ", room " + room.getRoomNumber() + ", bed " + allocation.getBed().getBedNumber()
                + (room.getBlock() == null ? "" : ", block " + room.getBlock());
        String text = description == null ? "" : description.strip();
        return tickets.create(HelpdeskActor.of(accountId, authorities),
                new CreateHelpdeskTicketRequest(categoryId, subject, where + "\n\n" + text, priority));
    }
}
