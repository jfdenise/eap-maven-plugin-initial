/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.eap.plugin.goals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
/**
 * Package an EAP server.
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends org.wildfly.plugin.provision.PackageServerMojo {

    @Parameter(alias = "channels", required = false, property = PropertyNames.JBOSS_EAP_PROVISIONING_CHANNELS)
    List<String>  channels = Collections.emptyList();

    @Override
    protected void enrichRepositories() throws MojoExecutionException {
        // NO-OP
    }

    @Override
    protected void persistProvisioningState(Path jbossHome, ProvisioningConfig config) throws IOException,
            MojoExecutionException, XMLStreamException {
        if (artifactResolver instanceof ChannelMavenArtifactRepositoryManager) {
            try {
                ((ChannelMavenArtifactRepositoryManager) artifactResolver).done(jbossHome);
            } catch (IOException | MavenUniverseException ex) {
                throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
            }
        } else {
            super.persistProvisioningState(jbossHome, config);
        }
    }

    @Override
    protected MavenRepoManager buildArtifactResolver() throws MojoExecutionException{
        if (channels == null || channels.isEmpty()) {
            return super.buildArtifactResolver();
        } else {
            try {
                return new ChannelMavenArtifactRepositoryManager(project, channels, Paths.get(project.getBuild().getDirectory()), repoSystem, repoSession);
            } catch (MalformedURLException ex) {
                throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
            }
        }
    }
}
