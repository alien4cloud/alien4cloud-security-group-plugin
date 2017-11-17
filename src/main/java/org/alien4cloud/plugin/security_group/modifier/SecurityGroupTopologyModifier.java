package org.alien4cloud.plugin.security_group.modifier;

import static alien4cloud.utils.AlienUtils.safe;
import static org.alien4cloud.plugin.security_group.modifier.KubeTopologyUtils.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.CapabilityType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeCapabilityTypes;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import alien4cloud.paas.wf.util.WorkflowUtils;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.utils.AlienUtils;
import alien4cloud.utils.PropertyUtil;
import lombok.extern.java.Log;

/**
 * Add Security groups to all nodes with a tosca.capability.Endpoint with a port value.
 */
@Log
@Component(value = "security_group-modifier")
public class SecurityGroupTopologyModifier extends TopologyModifierSupport {

    public static final String A4C_KUBERNETES_MODIFIER_TAG = "a4c_kubernetes-modifier";


    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        // for each node type with capabilities of type endpoint
        // - add a org.alien4cloud.security_group.api.types.ServiceResource and weave depends_on relationships
        // - populate properties on the K8S AbstractContainer that host the container image
        Set<NodeTemplate> nodesWithEndpoint = getNodesWithCapabilityOfType(topology, NormativeCapabilityTypes.ENDPOINT, true);
        nodesWithEndpoint.forEach(nodeTemplate -> manageNodes(csar, topology, nodeTemplate));

    }


    /**
     * @param topology
     * @param type
     * @param manageInheritance true if you also want to consider type hierarchy (ie. include that inherit the given type).
     * @return a set of nodes that are of the given type (or inherit the given type if <code>manageInheritance</code> is true).
     */
    public static Set<NodeTemplate> getNodesWithCapabilityOfType(Topology topology, String type, boolean manageInheritance) {
        Set<NodeTemplate> result = Sets.newHashSet();
        for (NodeTemplate nodeTemplate : safe(topology.getNodeTemplates()).values()) {
            for ( Capability capa : nodeTemplate.getCapabilities().values()) {
                if(type.equals(capa.getType())) {
                    result.add(nodeTemplate);
                } else if (manageInheritance) {
                    CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, capa.getType());
                    if (capabilityType.getDerivedFrom().contains(type)) {
                        result.add(nodeTemplate);
                    }
                }
            }
        }
        return result;
    }

    private void manageNodes(Csar csar, Topology topology, NodeTemplate nodeTemplate) {

        Set<String> endpointNames = Sets.newHashSet();
        for (Map.Entry<String, Capability> e : AlienUtils.safe(nodeTemplate.getCapabilities()).entrySet()) {
            CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, e.getValue().getType());
            if (WorkflowUtils.isOfType(capabilityType, NormativeCapabilityTypes.ENDPOINT)) {
                endpointNames.add(e.getKey());
            }
        }
        // endpointNames.forEach(endpointName -> manageContainerEndpoint(csar, topology, containerNodeTemplate, endpointName, containerRuntimeNodeTemplate, deploymentNodeTemplate, allContainerNodes));
    }

    /**
     * Each capability of type endpoint is considered for a given node of type container.
     */
    private void manageContainerEndpoints(Csar csar, Topology topology, NodeTemplate containerNodeTemplate, NodeTemplate containerRuntimeNodeTemplate, NodeTemplate deploymentNodeTemplate, Set<NodeTemplate> allContainerNodes) {
        // find every endpoint
        Set<String> endpointNames = Sets.newHashSet();
        for (Map.Entry<String, Capability> e : AlienUtils.safe(containerNodeTemplate.getCapabilities()).entrySet()) {
            CapabilityType capabilityType = ToscaContext.get(CapabilityType.class, e.getValue().getType());
            if (WorkflowUtils.isOfType(capabilityType, NormativeCapabilityTypes.ENDPOINT)) {
                endpointNames.add(e.getKey());
            }
        }
        endpointNames.forEach(endpointName -> manageContainerEndpoint(csar, topology, containerNodeTemplate, endpointName, containerRuntimeNodeTemplate, deploymentNodeTemplate, allContainerNodes));
    }

    /**
     * For a given endpoint capability of a node of type container, we must create a Service node.
     */
    private void manageContainerEndpoint(Csar csar, Topology topology, NodeTemplate containerNodeTemplate, String endpointName, NodeTemplate containerRuntimeNodeTemplate, NodeTemplate deploymentNodeTemplate, Set<NodeTemplate> allContainerNodes) {
        // fill the ports map of the hosting K8S AbstractContainer
        AbstractPropertyValue port = containerNodeTemplate.getCapabilities().get(endpointName).getProperties().get("port");
        ComplexPropertyValue portPropertyValue = new ComplexPropertyValue(Maps.newHashMap());
        portPropertyValue.getValue().put("containerPort", port);
        portPropertyValue.getValue().put("name", new ScalarPropertyValue(generateKubeName(endpointName)));
        appendNodePropertyPathValue(csar, topology, containerRuntimeNodeTemplate, "container.ports", portPropertyValue);

        // add an abstract service node
        NodeTemplate serviceNode = addNodeTemplate(csar, topology, containerNodeTemplate.getName() + "_" + endpointName + "_Service", K8S_TYPES_ABSTRACT_SERVICE, K8S_CSAR_VERSION);
        setNodeTagValue(serviceNode, A4C_KUBERNETES_MODIFIER_TAG, "Proxy of node <" + containerNodeTemplate.getName() + "> capability <" + endpointName + ">");
        // setNodeTagValue(serviceNode, A4C_KUBERNETES_MODIFIER_TAG_SERVICE_ENDPOINT, endpointName);

        // fill properties of service
        setNodePropertyPathValue(csar, topology, serviceNode, "metadata.name", new ScalarPropertyValue(generateUniqueKubeName(serviceNode.getName())));
        setNodePropertyPathValue(csar, topology, serviceNode, "spec.service_type", new ScalarPropertyValue("NodePort"));
        // get the "pod name"
        AbstractPropertyValue podName = PropertyUtil.getPropertyValueFromPath(AlienUtils.safe(deploymentNodeTemplate.getProperties()), "metadata.name");
        setNodePropertyPathValue(csar, topology, serviceNode, "spec.selector.app", podName);

        // fill port list
        Map<String, Object> portEntry = Maps.newHashMap();
        String portName = generateKubeName(endpointName);
        portEntry.put("name", new ScalarPropertyValue(portName));
        portEntry.put("targetPort", new ScalarPropertyValue(portName));
        portEntry.put("port", port);
        ComplexPropertyValue complexPropertyValue = new ComplexPropertyValue(portEntry);
        appendNodePropertyPathValue(csar, topology, serviceNode, "spec.ports", complexPropertyValue);

        // find the deployment node parent of the container
        NodeTemplate deploymentHost = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, containerNodeTemplate, K8S_TYPES_ABSTRACT_DEPLOYMENT);
        // add a depends_on relationship between service and the deployment unit
        addRelationshipTemplate(csar, topology, serviceNode, deploymentHost.getName(), NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");

        // we should find each relationship that targets this endpoint and add a dependency between both deployments
        for (NodeTemplate containerSourceCandidateNodeTemplate : allContainerNodes) {
            if (containerSourceCandidateNodeTemplate.getName().equals(containerNodeTemplate.getName())) {
                // don't consider the current container (owner of the endpoint)
                continue;
            }

            for (RelationshipTemplate relationship : AlienUtils.safe(containerSourceCandidateNodeTemplate.getRelationships()).values()) {
                if (relationship.getTarget().equals(containerNodeTemplate.getName()) && relationship.getTargetedCapabilityName().equals(endpointName)) {
                    // we need to add a depends_on between the source deployment and the service (if not already exist)
                    NodeTemplate sourceDeploymentHost = TopologyNavigationUtil.getHostOfTypeInHostingHierarchy(topology, containerSourceCandidateNodeTemplate, K8S_TYPES_ABSTRACT_DEPLOYMENT);
                    if (!TopologyNavigationUtil.hasRelationship(sourceDeploymentHost, serviceNode.getName(), "dependency", "feature")) {
                        addRelationshipTemplate(csar, topology, sourceDeploymentHost, serviceNode.getName(), NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");
                    }
                }
            }
        }
    }

}
