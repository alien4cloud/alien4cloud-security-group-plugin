package org.alien4cloud.plugin.security_group.csar;

import org.springframework.stereotype.Component;

import alien4cloud.plugin.archives.AbstractArchiveProviderPlugin;

@Component("security-group-archives-provider")
public class SecurityGroupArchivesProvider extends AbstractArchiveProviderPlugin {

    @Override
    protected String[] getArchivesPaths() {
        return new String[] { "csar" };
    }
}
