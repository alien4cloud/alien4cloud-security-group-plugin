package org.alien4cloud.plugin.security_group;

import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.java.Log;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.alien4cloud.plugin.security_group.modifier.SecurityGroupTopologyUtils.*;

/**
 * Just for tests : simulate matching by replacing all abstract nodes by it's concrete implem.
 */
@Log
@Component(value = "security-group-automatching-modifier")
public class SecurityGroupAutomatchingTopologyModifier extends TopologyModifierSupport {

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        // replace each abstract SecurityGroup by the AWS implementation
        Set<NodeTemplate> containerNodes = TopologyNavigationUtil.getNodesOfType(topology, SECGGROUP_TYPES_ABSTRACT, false);
        containerNodes.forEach(nodeTemplate -> {
            replaceNode(csar, topology, nodeTemplate, AWS_SECGGROUP_TYPES, SECGROUP_CSAR_VERSION);
        });
        // replace each abstract SecurityGroupRule by the AWS implementation
        Set<NodeTemplate> serviceNodes = TopologyNavigationUtil.getNodesOfType(topology, SECGGROUPRULE_TYPES_ABSTRACT, false);
        serviceNodes.forEach(nodeTemplate -> {
            replaceNode(csar, topology, nodeTemplate, AWS_SECGGROUPRULE_TYPES, SECGROUP_CSAR_VERSION);
        });
    }
}