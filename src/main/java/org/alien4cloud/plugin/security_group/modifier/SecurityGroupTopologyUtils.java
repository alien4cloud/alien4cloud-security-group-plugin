package org.alien4cloud.plugin.security_group.modifier;

import java.util.List;
import java.util.Map;

import org.alien4cloud.tosca.model.definitions.PropertyValue;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;

/**
 * A utility to browse topologies.
 */
public class SecurityGroupTopologyUtils {

    // SecurityGroup abstract types
    public static final String SECGGROUP_TYPES_ABSTRACT = "org.alien4cloud.nodes.SecurityGroup";
    public static final String SECGGROUPRULE_TYPES_ABSTRACT = "org.alien4cloud.nodes.SecurityGroupRule";

    // SecurityGroup relationship types
    public static final String REL_SECGROUP_CONNECTS_TO_COMPUTE = "org.alien4cloud.relationships.SecurityGroupConnectsToCompute";
    public static final String REL_SECGROUP_CONNECTS_TO_COMPUTE_AWS = "org.alien4cloud.relationships.aws.SecurityGroupConnectsToCompute";
    public static final String REL_SECGROUPRULE_HOSTED_ON_SECGROUP = "org.alien4cloud.relationships.SecGroupRuleHostedOnSecGroup";

    public static final String SECGROUP_REQUIREMENT_ENDPOINT_NAME = "endpoint";
    public static final String SECGROUPRULE_REQUIREMENT_SECURITY_GROUP_RULES_NAME = "security_group_rules";

    public static final String SECGROUPRULES_CAPABILITY = "org.alien4cloud.capabilities.SecurityGroupRules";

    // SecurityGroup AWS concrete types
    public static final String AWS_SECGGROUP_TYPES = "org.alien4cloud.nodes.aws.SecurityGroup";
    public static final String AWS_SECGGROUPRULE_TYPES = "org.alien4cloud.nodes.aws.SecurityGroupRule";

    // TODO: should be parsed from src/main/resources/csar/tosca.yml or query ES to get the last version of this CSAR
    public static final String SECGROUP_CSAR_VERSION = "2.0.0";

    /**
     * Recursively get the root Object value eventually hosted by a PropertyValue. If the value is a collection (ListPropertyValue, AbstractPropertyValue) then returns a collection of Objects.
     */
    public static Object getValue(Object value) {
        Object valueObject = value;
        if (value instanceof PropertyValue) {
            valueObject = getValue(((PropertyValue) value).getValue());
        } else if (value instanceof Map<?, ?>) {
            Map<String, Object> newMap = Maps.newHashMap();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) valueObject).entrySet()) {
                newMap.put(entry.getKey(), getValue(entry.getValue()));
            }
            valueObject = newMap;
        } else if (value instanceof List<?>) {
            List<Object> newList = Lists.newArrayList();
            for (Object entry : (List<Object>) valueObject) {
                newList.add(getValue(entry));
            }
            valueObject = newList;
        }
        return valueObject;
    }

}
