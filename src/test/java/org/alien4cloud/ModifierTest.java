package org.alien4cloud;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(format = "pretty", tags = { "~@Ignore" }, features = {
        // "src/test/resources/org/alien4cloud/security_group/modifiers/features"
        "src/test/resources/org/alien4cloud/security_group/modifiers/features/00-modifiers-chain.feature"
//        "src/test/resources/org/alien4cloud/security_group/modifiers/features/01-location-topology-modifier.feature"
//        "src/test/resources/org/alien4cloud/security_group/modifiers/features/02-anti-affinity-modifier.feature"
//        "src/test/resources/org/alien4cloud/security_group/modifiers/features/03-final-modifier.feature"
})
public class ModifierTest {

}