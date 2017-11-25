package org.alien4cloud.plugin.security_group.modifier;

import javax.annotation.Resource;

import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.java.Log;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.ToscaTypeUtils;
import org.springframework.stereotype.Component;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * Recreate workflow for security groups and relationships to make them concrete.
 */
@Log
@Component(value = "security_group-final-modifier")
public class SecurityGroupFinalTopologyModifier extends TopologyModifierSupport {

    @Resource
    private WorkflowsBuilderService workflowBuilderService;

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {
        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        WorkflowsBuilderService.TopologyContext topologyContext = workflowBuilderService.buildTopologyContext(topology, csar);
        for (NodeTemplate nodeTemplate : safe(topology.getNodeTemplates()).values()) {
            if (isSecurityGroupOrRuleType(nodeTemplate)) {
                rebuildNodeWorkflow(topology, csar, topologyContext, nodeTemplate);
            }
        }
    }

    private void rebuildNodeWorkflow(Topology topology, Csar csar, WorkflowsBuilderService.TopologyContext topologyContext, NodeTemplate nodeTemplate) {
        // Rebuild the workflow of the nodeTemplate.
        log.fine("Rebuild Workflow for node template " + nodeTemplate.getName());
        workflowBuilderService.removeNode(topology, csar, nodeTemplate.getName());
        workflowBuilderService.addNode(topologyContext, nodeTemplate.getName());

        for (RelationshipTemplate relationshipTemplate : safe(nodeTemplate.getRelationships()).values()) {
            if (relationshipTemplate.getType().equals(SecurityGroupTopologyUtils.REL_SECGROUP_CONNECTS_TO_COMPUTE)) {
                // Replace generic relationship with aws implementation relationship
                // TODO better support for multiple locations
                log.fine("Rebuild relationship " + relationshipTemplate.getName() + " workflow of node template " + nodeTemplate.getName());
                nodeTemplate.getRelationships().remove(relationshipTemplate.getName());
                addRelationshipTemplate(csar, topology, nodeTemplate, relationshipTemplate.getTarget(),
                        SecurityGroupTopologyUtils.REL_SECGROUP_CONNECTS_TO_COMPUTE_AWS, relationshipTemplate.getRequirementName(),
                        relationshipTemplate.getTargetedCapabilityName());
            } else {
                workflowBuilderService.addRelationship(topologyContext, nodeTemplate.getName(), relationshipTemplate.getName());
            }
        }
    }

    private boolean isSecurityGroupType(NodeTemplate nodeTemplate) {
        NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
        return ToscaTypeUtils.isOfType(nodeType, SecurityGroupTopologyUtils.SECGGROUP_TYPES_ABSTRACT);
    }

    private boolean isSecurityGroupRuleType(NodeTemplate nodeTemplate) {
        NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
        return ToscaTypeUtils.isOfType(nodeType, SecurityGroupTopologyUtils.SECGGROUPRULE_TYPES_ABSTRACT);
    }

    private boolean isSecurityGroupOrRuleType(NodeTemplate nodeTemplate) {
        return isSecurityGroupType(nodeTemplate) || isSecurityGroupRuleType(nodeTemplate);
    }
}
