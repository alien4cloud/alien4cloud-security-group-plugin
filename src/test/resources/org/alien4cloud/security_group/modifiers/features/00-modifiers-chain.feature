Feature: Topology modifiers chain tests
  # apply the the whole modifier chain to the initial topologies
  # not really a test but useful for developing

  Background:
    Given I am authenticated with "ADMIN" role
    Given I add and import a GIT repository with url "https://github.com/alien4cloud/tosca-normative-types.git" usr "" pwd "" stored "false" and locations
      | branchId | subPath |
      | master   |         |
    # Given I add and import a GIT repository with url "https://github.com/alien4cloud/samples.git" usr "" pwd "" stored "false" and locations
    #   | branchId | subPath |
    #   | master | mongo |
    Given I upload unzipped CSAR from path "src/main/resources/csar"
    Given I upload unzipped CSAR from path "src/test/resources/csar/mongo-type.yml"
    Given I upload unzipped CSAR from path "src/test/resources/csar/nodejs-type.yml"
    Given I upload unzipped CSAR from path "src/test/resources/csar/nodecellar-type.yml"

  Scenario: Apply security group modifier on a nodecellar topology
    Given I upload unzipped CSAR from path "src/test/resources/data/03-nodecellar/30-nodecellar-template.yml"
    And I get the topology related to the CSAR with name "30-nodecellar-template" and version "2.0.0"
    When I execute the modifier "security_group-modifier" on the current topology
    And I execute the modifier "security_group-final-modifier" on the current topology

  # Scenario: Apply each modifiers on a simple topology containing 2 apache with an anti-affinity policy
  #   Given I upload unzipped CSAR from path "src/test/resources/data/02-two-apache/1-initial.yaml"
  #   And I get the topology related to the CSAR with name "initial" and version "2.0.0"
  #   When I execute the modifier "kubernetes-modifier" on the current topology
  #   And I execute the modifier "kubernetes-automatching-modifier" on the current topology
  #   And I match the policy named "Placement" to the concrete policy of type "org.alien4cloud.kubernetes.api.policies.AntiAffinityLabel"
  #   And I set the policy "Placement" property "level" to "host"
  #   And I execute the modifier "kubernetes-anti-affinity-modifier" on the current topology
  #   And I execute the modifier "kubernetes-final-modifier" on the current topology

  # Scenario: Apply each modifiers on a topology containing 1 nodecellar connected to 1 mongo
  #   Given I upload unzipped CSAR from path "src/test/resources/data/03-1nodecellar-1mongo/1-initial.yaml"
  #   And I get the topology related to the CSAR with name "initial" and version "2.0.0"
  #   When I execute the modifier "kubernetes-modifier" on the current topology
  #   And I execute the modifier "kubernetes-automatching-modifier" on the current topology
  #   And I execute the modifier "kubernetes-final-modifier" on the current topology

  # Scenario: Apply each modifiers on a topology containing 2 nodecellar connected to 2 mongo with an anti-affinity policy
  #   Given I upload unzipped CSAR from path "src/test/resources/data/04-2nodecellar-2mongo/1-initial.yaml"
  #   And I get the topology related to the CSAR with name "initial" and version "2.0.0"
  #   When I execute the modifier "kubernetes-modifier" on the current topology
  #   And I execute the modifier "kubernetes-automatching-modifier" on the current topology
  #   And I match the policy named "AntiAffinity" to the concrete policy of type "org.alien4cloud.kubernetes.api.policies.AntiAffinityLabel"
  #   And I set the policy "AntiAffinity" property "level" to "host"
  #   And I execute the modifier "kubernetes-anti-affinity-modifier" on the current topology
  #   And I execute the modifier "kubernetes-final-modifier" on the current topology