package com.stacc.backend.approval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Workflow definitions, loaded with their steps and roles when those are needed. */
public interface ApprovalWorkflowDefinitionRepository extends JpaRepository<ApprovalWorkflowDefinition, Long> {

    boolean existsByCode(String code);

    @Query("""
            select distinct workflow
            from ApprovalWorkflowDefinition workflow
            left join fetch workflow.steps step
            left join fetch step.approverRole
            where workflow.id = :workflowId
            """)
    Optional<ApprovalWorkflowDefinition> findWithStepsById(@Param("workflowId") Long workflowId);

    @Query("""
            select distinct workflow
            from ApprovalWorkflowDefinition workflow
            left join fetch workflow.steps step
            left join fetch step.approverRole
            where workflow.code = :code
            """)
    Optional<ApprovalWorkflowDefinition> findWithStepsByCode(@Param("code") String code);

    /** Every workflow with its steps, by code, for the admin list. */
    @Query("""
            select distinct workflow
            from ApprovalWorkflowDefinition workflow
            left join fetch workflow.steps step
            left join fetch step.approverRole
            order by workflow.code asc, workflow.id asc
            """)
    List<ApprovalWorkflowDefinition> findAllWithSteps();
}
