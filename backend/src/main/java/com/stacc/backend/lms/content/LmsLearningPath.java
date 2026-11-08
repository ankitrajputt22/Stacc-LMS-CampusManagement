package com.stacc.backend.lms.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where one student stands in one course, worked out from what they can see: the published modules in course order,
 * their published pages, and the pages the student completed. Nothing here is stored.
 *
 * <p>A module is complete when it is open and every required page in it is completed (a module without required pages
 * is complete once open). A module that requires the previous one is locked until the previous module is complete;
 * the first module is never locked. Progress counts the required pages of every visible module.
 */
public final class LmsLearningPath {

    /** One module's state for the student. {@code waitingFor} names the module to complete first, when locked. */
    public record ModuleState(boolean locked, boolean completed, String waitingFor) {
    }

    /** The next required page to complete, in course order, in a module that is open. */
    public record NextPage(Long pageId, String title, String moduleTitle) {
    }

    private final Map<Long, ModuleState> modules = new HashMap<>();
    private final Set<Long> completedPageIds;
    private final int requiredPages;
    private final int completedRequiredPages;
    private final NextPage next;

    private LmsLearningPath(List<LmsModule> modulesInOrder, List<LmsTopic> pages, Set<Long> completedPageIds) {
        this.completedPageIds = Set.copyOf(completedPageIds);
        int required = 0;
        int completed = 0;
        NextPage firstOpen = null;
        LmsModule previous = null;
        ModuleState previousState = null;
        for (LmsModule module : modulesInOrder) {
            boolean locked = module.isRequiresPrevious() && previous != null && !previousState.completed();
            boolean allRequiredDone = true;
            for (LmsTopic page : pages) {
                if (!page.getLmsModule().getId().equals(module.getId()) || !page.isRequired()) {
                    continue;
                }
                required++;
                if (this.completedPageIds.contains(page.getId())) {
                    completed++;
                } else {
                    allRequiredDone = false;
                    if (firstOpen == null && !locked) {
                        firstOpen = new NextPage(page.getId(), page.getTitle(), module.getTitle());
                    }
                }
            }
            ModuleState state = new ModuleState(locked, !locked && allRequiredDone, locked ? previous.getTitle() : null);
            modules.put(module.getId(), state);
            previous = module;
            previousState = state;
        }
        this.requiredPages = required;
        this.completedRequiredPages = completed;
        this.next = firstOpen;
    }

    /**
     * @param modulesInOrder the modules the student can see, in course order
     * @param pages the pages the student can see, in module order
     * @param completedPageIds the pages the student completed
     */
    public static LmsLearningPath of(List<LmsModule> modulesInOrder, List<LmsTopic> pages, Set<Long> completedPageIds) {
        return new LmsLearningPath(modulesInOrder, pages, completedPageIds);
    }

    /** The module's state, or a locked state for a module that is not on the path (not visible to the student). */
    public ModuleState module(Long moduleId) {
        return modules.getOrDefault(moduleId, new ModuleState(true, false, null));
    }

    public boolean isOpen(Long moduleId) {
        return !module(moduleId).locked();
    }

    public boolean isCompleted(Long pageId) {
        return completedPageIds.contains(pageId);
    }

    public int requiredPages() {
        return requiredPages;
    }

    public int completedRequiredPages() {
        return completedRequiredPages;
    }

    /** The next required page to complete, or null when none is left in an open module. */
    public NextPage next() {
        return next;
    }
}
