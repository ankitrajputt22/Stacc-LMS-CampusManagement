package com.stacc.backend.grievance;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grievance categories and committees for admins (`GRIEVANCE_MANAGE`). Committee members are active accounts holding
 * `FACULTY` or `ADMIN`; adding and ending them is audited. Admins never read cases here: only committees do.
 */
@Service
@Transactional
public class GrievanceAdminService {

    static final String CATEGORY_NOT_FOUND = "Category not found.";
    static final String MEMBER_NOT_FOUND = "Committee member not found.";
    static final String NOT_ELIGIBLE = "Committee members are active faculty or admin accounts.";
    static final String ALREADY = "That account is already on this committee.";
    static final String CODE_TAKEN = "A category with this code already exists.";

    public record CommitteeMemberView(Long memberId, String loginId, Instant addedAt) {
    }

    public record CategoryView(Long categoryId, String code, String name, int responseDays, boolean active, List<CommitteeMemberView> committee) {
    }

    private final GrievanceCategoryRepository categories;
    private final GrievanceCommitteeMemberRepository members;
    private final UserAccountRepository accounts;
    private final AuditService audit;
    private final Clock clock;

    public GrievanceAdminService(GrievanceCategoryRepository categories, GrievanceCommitteeMemberRepository members,
            UserAccountRepository accounts, AuditService audit, Clock clock) {
        this.categories = categories;
        this.members = members;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories() {
        Map<Long, List<GrievanceCommitteeMember>> committees = members.findCurrent().stream()
                .collect(Collectors.groupingBy(member -> member.getCategory().getId()));
        return categories.findAllByOrderByNameAsc().stream().map(category -> view(category, committees.getOrDefault(category.getId(), List.of()))).toList();
    }

    public CategoryView create(String code, String name, int responseDays) {
        try {
            return view(categories.saveAndFlush(valid(() -> new GrievanceCategory(code, name, responseDays, now()))), List.of());
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, CODE_TAKEN);
        }
    }

    public CategoryView change(Long categoryId, String name, int responseDays, boolean active) {
        GrievanceCategory category = category(categoryId);
        valid(() -> {
            category.change(name, responseDays, active);
            return category;
        });
        return viewOf(category);
    }

    public CategoryView addMember(Long adminId, Long categoryId, String loginId) {
        GrievanceCategory category = category(categoryId);
        UserAccount account = (loginId == null ? Optional.<UserAccount>empty() : accounts.findWithRolesAndPermissionsByLoginId(loginId.strip()))
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE)
                .filter(found -> found.hasRole(RoleName.FACULTY) || found.hasRole(RoleName.ADMIN))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_ELIGIBLE));
        GrievanceCommitteeMember saved;
        try {
            saved = members.saveAndFlush(new GrievanceCommitteeMember(category, account, now()));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY);
        }
        audit.record(adminId, AuditAction.GRIEVANCE_COMMITTEE_MEMBER_ADDED, "GRIEVANCE_CATEGORY", category.getId(),
                "Committee member added to " + category.getCode() + " (member " + saved.getId() + ").");
        return viewOf(category);
    }

    public CategoryView endMember(Long adminId, Long memberId) {
        GrievanceCommitteeMember member = (memberId == null ? Optional.<GrievanceCommitteeMember>empty() : members.findById(memberId))
                .filter(found -> found.getEndedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MEMBER_NOT_FOUND));
        member.end(now());
        members.flush();
        GrievanceCategory category = member.getCategory();
        audit.record(adminId, AuditAction.GRIEVANCE_COMMITTEE_MEMBER_ENDED, "GRIEVANCE_CATEGORY", category.getId(),
                "Committee member ended on " + category.getCode() + " (member " + member.getId() + ").");
        return viewOf(category);
    }

    private CategoryView viewOf(GrievanceCategory category) {
        return view(category, members.findCurrent().stream().filter(member -> member.getCategory().getId().equals(category.getId())).toList());
    }

    private static CategoryView view(GrievanceCategory category, List<GrievanceCommitteeMember> committee) {
        return new CategoryView(category.getId(), category.getCode(), category.getName(), category.getResponseDays(), category.isActive(),
                committee.stream().map(member -> new CommitteeMemberView(member.getId(), member.getAccount().getLoginId(), member.getAddedAt())).toList());
    }

    private GrievanceCategory category(Long categoryId) {
        return (categoryId == null ? Optional.<GrievanceCategory>empty() : categories.findById(categoryId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CATEGORY_NOT_FOUND));
    }

    private static <T> T valid(Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
