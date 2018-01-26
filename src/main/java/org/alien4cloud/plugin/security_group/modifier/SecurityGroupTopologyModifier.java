package org.alien4cloud.plugin.security_group.modifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.utils.PropertyUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.java.Log;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.CapabilityType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeCapabilityTypes;
import org.alien4cloud.tosca.normative.constants.NormativeComputeConstants;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.NodeTemplateUtils;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.alien4cloud.tosca.utils.ToscaTypeUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * Add Security groups to all nodes with a tosca.capability.Endpoint with a port value.
 */
@Log
@Component(value = "security_group-modifier")
public class SecurityGroupTopologyModifier extends TopologyModifierSupport {

    public static final String SECGROUPRULE_PUBLIC_CIDR = "0.0.0.0/0";
    public static final String SECGROUPRULE_SG_PREFIX = "_a4c_";

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        // for each node type with capabilities of type endpoint
        // - add a org.alien4cloud.security_group.api.types.ServiceResource and weave depends_on relationships
        // - populate properties on the K8S AbstractContainer that host the container image
        Set<NodeTemplate> computes = TopologyNavigationUtil.getNodesOfType(topology, NormativeComputeConstants.COMPUTE_TYPE, true);

        // Construct a map with all node templates that have defined an endpoint group by computes
        Map<String, List<NodeTemplate>> endpointsPerCompute = Maps.newHashMap();
        for (NodeTemplate compute : computes) {
            endpointsPerCompute.put(compute.getName(), this.retrieveHostedNodesWithEndpoint(topology, compute));
        }

        this.transformTopology(csar, topology, endpointsPerCompute);
    }

    /**
     * Recursively return all nodeTemplates which own a capability of type tosca.capabilities.Endpoint that have a port value.
     *
     * @param topology The entire topology
     * @param host     The node template of the host
     * @return A list a node templates which own a capability of type tosca.capabilities.Endpoint that have a port value.
     */
    private List<NodeTemplate> retrieveHostedNodesWithEndpoint(Topology topology, NodeTemplate host) {
        List<NodeTemplate> nodesWithEndpoints = Lists.newArrayList();
        List<NodeTemplate> hostedNodes = TopologyNavigationUtil.getHostedNodes(topology, host.getName());
        for (NodeTemplate hosted : hostedNodes) {
            nodesWithEndpoints.addAll(this.retrieveHostedNodesWithEndpoint(topology, hosted));
        }
        Capability endpointCapability = NodeTemplateUtils.getCapabilityByType(host, NormativeCapabilityTypes.ENDPOINT);
        if (endpointCapability != null && isPropertyNotNull(endpointCapability.getProperties(), "port")) {
            nodesWithEndpoints.add(host);
        }
        return nodesWithEndpoints;
    }

    private boolean isPropertyNotNull(Map<String, AbstractPropertyValue> properties, String key) {
        if (properties.containsKey(key)) {
            AbstractPropertyValue abstractPortPropertyValue = properties.get(key);
            if (abstractPortPropertyValue instanceof ScalarPropertyValue && ((ScalarPropertyValue) abstractPortPropertyValue).getValue() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transform the topology by adding security groups
     *
     * @param csar
     * @param topology
     * @param endpointsPerCompute
     */
    private void transformTopology(Csar csar, Topology topology, Map<String, List<NodeTemplate>> endpointsPerCompute) {
        Map<String, NodeTemplate> computeSecgroup = Maps.newHashMap();

        // Create security groups for all computes
        for (String computeName : endpointsPerCompute.keySet()) {
            NodeTemplate secgroupNodeTemplate = this.createSecurityGroupAttachedToCompute(csar, topology, computeName);
            computeSecgroup.put(computeName, secgroupNodeTemplate);
        }

        // Looking for endpoints that are target of a relationships
        for (List<NodeTemplate> templates : endpointsPerCompute.values()) {
            for (NodeTemplate nodeTemplate : templates) {
                NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
                if (!ToscaTypeUtils.isOfType(nodeType, SecurityGroupTopologyUtils.SECGGROUP_TYPES_ABSTRACT)) {
                    List<RelationshipTemplate> endpointRelationships = this.getRelationshipWithEndpointTarget(nodeTemplate);
                    for (RelationshipTemplate relationship : endpointRelationships) {
                        NodeTemplate targetNodeTemplate = topology.getNodeTemplates().get(relationship.getTarget());
                        this.createSecurityGroupRule(csar, topology, nodeTemplate, targetNodeTemplate, relationship, computeSecgroup);
                    }
                }
            }
        }

        // Add public rule
        for (List<NodeTemplate> templates : endpointsPerCompute.values()) {
            for (NodeTemplate nodeTemplate : templates) {
                for (Map.Entry<String, Capability> entrySet : nodeTemplate.getCapabilities().entrySet()) {
                    CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, entrySet.getValue().getType());
                    if (ToscaTypeUtils.isOfType(capabilityType, NormativeCapabilityTypes.ENDPOINT)) {
                        if (this.isPublicNetwork(entrySet.getValue())) {
                            this.createPublicSecurityGroupRule(csar, topology, nodeTemplate, entrySet.getKey(), entrySet.getValue(), computeSecgroup);
                        }
                    }
                }
            }
        }
    }

    private List<RelationshipTemplate> getRelationshipWithEndpointTarget(NodeTemplate template) {
        List<RelationshipTemplate> list = Lists.newArrayList();
        for (RelationshipTemplate relationshipTemplate : safe(template.getRelationships()).values()) {
            CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, relationshipTemplate.getRequirementType());
            if (ToscaTypeUtils.isOfType(capabilityType, NormativeCapabilityTypes.ENDPOINT)) {
                list.add(relationshipTemplate);
            }
        }
        return list;
    }

    private NodeTemplate createPublicSecurityGroupRule(Csar csar, Topology topology, NodeTemplate nodeTemplate, String capabilityName, Capability capability,
            Map<String, NodeTemplate> computeSecgroup) {

        String ruleName = nodeTemplate.getName() + "_" + capabilityName;

        // Create secgroup rule
        NodeTemplate sgr = addNodeTemplate(csar, topology, ruleName, SecurityGroupTopologyUtils.SECGGROUPRULE_TYPES_ABSTRACT,
                SecurityGroupTopologyUtils.SECGROUP_CSAR_VERSION);

        // Fill the properties
        setNodePropertyPathValue(csar, topology, sgr, "name", new ScalarPropertyValue(ruleName));
        setNodePropertyPathValue(csar, topology, sgr, "protocol", capability.getProperties().get("protocol"));
        setNodePropertyPathValue(csar, topology, sgr, "direction", new ScalarPropertyValue("inbound"));
        setNodePropertyPathValue(csar, topology, sgr, "port", PropertyUtil.getPropertyValueFromPath(safe(capability.getProperties()), "port"));
        setNodePropertyPathValue(csar, topology, sgr, "remote", new ScalarPropertyValue(SECGROUPRULE_PUBLIC_CIDR));

        // Add relationship to Secgroup
        NodeTemplate targetCompute = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, nodeTemplate, NormativeComputeConstants.COMPUTE_TYPE);
        NodeTemplate targetSecgroup = computeSecgroup.get(targetCompute.getName());
        String secgroupCapabilityName = this.getCapabilityNameFromType(targetSecgroup, SecurityGroupTopologyUtils.SECGROUPRULES_CAPABILITY);
        this.addRelationshipTemplate(csar, topology, sgr, targetSecgroup.getName(), SecurityGroupTopologyUtils.REL_SECGROUPRULE_HOSTED_ON_SECGROUP,
                SecurityGroupTopologyUtils.SECGROUPRULE_REQUIREMENT_SECURITY_GROUP_RULES_NAME, secgroupCapabilityName);

        return sgr;

    }

    private NodeTemplate createSecurityGroupRule(Csar csar, Topology topology, NodeTemplate source, NodeTemplate target, RelationshipTemplate relationship,
            Map<String, NodeTemplate> computeSecgroup) {
        Capability capability = NodeTemplateUtils.getCapabilityByType(target, relationship.getRequirementType());
        String ruleName = target.getName() + "_" + relationship.getTargetedCapabilityName();
        NodeTemplate sgr = addNodeTemplate(csar, topology, ruleName, SecurityGroupTopologyUtils.SECGGROUPRULE_TYPES_ABSTRACT,
                SecurityGroupTopologyUtils.SECGROUP_CSAR_VERSION);
        setNodePropertyPathValue(csar, topology, sgr, "name", new ScalarPropertyValue(ruleName));
        setNodePropertyPathValue(csar, topology, sgr, "protocol", capability.getProperties().get("protocol"));
        setNodePropertyPathValue(csar, topology, sgr, "direction", new ScalarPropertyValue("inbound"));
        setNodePropertyPathValue(csar, topology, sgr, "port", PropertyUtil.getPropertyValueFromPath(safe(capability.getProperties()), "port"));

        if (this.isPublicNetwork(capability)) {
            setNodePropertyPathValue(csar, topology, sgr, "remote", new ScalarPropertyValue(SECGROUPRULE_PUBLIC_CIDR));
        } else {
            NodeTemplate sourceCompute = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, source, NormativeComputeConstants.COMPUTE_TYPE);
            NodeTemplate sourceSecgroup = computeSecgroup.get(sourceCompute.getName());
            String uniqueName = ((ScalarPropertyValue) sourceSecgroup.getProperties().get("name")).getValue(); // get the unique name from the property
            setNodePropertyPathValue(csar, topology, sgr, "remote", new ScalarPropertyValue(SECGROUPRULE_SG_PREFIX + uniqueName));

            // Add a dependency to make sure that the secgroup is already created before creating the rule.
            addRelationshipTemplate(csar, topology, sgr, sourceSecgroup.getName(), NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");
        }

        // Add relationship to Secgroup
        NodeTemplate targetCompute = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, target, NormativeComputeConstants.COMPUTE_TYPE);
        NodeTemplate targetSecgroup = computeSecgroup.get(targetCompute.getName());
        String capabilityName = this.getCapabilityNameFromType(targetSecgroup, SecurityGroupTopologyUtils.SECGROUPRULES_CAPABILITY);
        this.addRelationshipTemplate(csar, topology, sgr, targetSecgroup.getName(), SecurityGroupTopologyUtils.REL_SECGROUPRULE_HOSTED_ON_SECGROUP,
                SecurityGroupTopologyUtils.SECGROUPRULE_REQUIREMENT_SECURITY_GROUP_RULES_NAME, capabilityName);

        return sgr;
    }

    private boolean isPublicNetwork(Capability capability) {
        String networkName = ((ScalarPropertyValue) capability.getProperties().get("network_name")).getValue();
        if (StringUtils.equalsIgnoreCase("public", networkName)) {
            return true;
        }
        return false;
    }

    private NodeTemplate createSecurityGroupAttachedToCompute(Csar csar, Topology topology, String computeName) {
        // Add a new security group node
        NodeTemplate secgroupNodeTemplate = addNodeTemplate(csar, topology, computeName + "_Secgroup", SecurityGroupTopologyUtils.SECGGROUP_TYPES_ABSTRACT,
                SecurityGroupTopologyUtils.SECGROUP_CSAR_VERSION);

        // Set the name
        String secgroupName = computeName + "_Secgroup-" + UUID.randomUUID().toString();
        setNodePropertyPathValue(csar, topology, secgroupNodeTemplate, "name", new ScalarPropertyValue(secgroupName));

        // Link to the compute
        NodeTemplate compute = topology.getNodeTemplates().get(computeName);
        String capabilityName = this.getCapabilityNameFromType(compute, NormativeCapabilityTypes.ENDPOINT_ADMIN);
        this.addRelationshipTemplate(csar, topology, secgroupNodeTemplate, computeName, SecurityGroupTopologyUtils.REL_SECGROUP_CONNECTS_TO_COMPUTE,
                SecurityGroupTopologyUtils.SECGROUP_REQUIREMENT_ENDPOINT_NAME, capabilityName);

        return secgroupNodeTemplate;
    }

    private String getCapabilityNameFromType(NodeTemplate nodeTemplate, String capabilityTypeName) {
        for (String capabilityName : safe(nodeTemplate.getCapabilities()).keySet()) {
            Capability capability = nodeTemplate.getCapabilities().get(capabilityName);
            if (capability.getType().equals(capabilityTypeName)) {
                return capabilityName;
            }
            // if the type does not strictly equals we should check the derived from element
            CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, capability.getType());
            if (ToscaTypeUtils.isOfType(capabilityType, capabilityTypeName)) {
                return capabilityName;
            }
        }
        return null;
    }

}
