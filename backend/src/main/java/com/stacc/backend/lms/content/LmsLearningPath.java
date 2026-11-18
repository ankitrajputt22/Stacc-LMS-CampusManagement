package com.stacc.backend.lms.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LmsLearningPath {

    public record ModuleState(boolean locked, boolean completed, String waitingFor) {
    }

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

    public static LmsLearningPath of(List<LmsModule> modulesInOrder, List<LmsTopic> pages, Set<Long> completedPageIds) {
        return new LmsLearningPath(modulesInOrder, pages, completedPageIds);
    }

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

    public NextPage next() {
        return next;
    }
}
