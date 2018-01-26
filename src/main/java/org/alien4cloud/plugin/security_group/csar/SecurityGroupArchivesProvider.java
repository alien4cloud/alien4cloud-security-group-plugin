package org.alien4cloud.plugin.security_group.csar;

import alien4cloud.plugin.archives.AbstractArchiveProviderPlugin;
import org.springframework.stereotype.Component;

@Component("security-group-archives-provider")
public class SecurityGroupArchivesProvider extends AbstractArchiveProviderPlugin {

    @Override
    protected String[] getArchivesPaths() {
        return new String[] { "csar" };
    }
}
