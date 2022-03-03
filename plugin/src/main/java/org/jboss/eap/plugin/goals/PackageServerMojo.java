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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.ProvisioningPlan;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.runtime.FeaturePackRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.ChannelSpec;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
/**
 * Package an EAP server.
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends org.wildfly.plugin.provision.PackageServerMojo {

    // TODO, update to proper universe GroupId, artifactId
    private static final ProducerSpec EAP_PRODUCER = FeaturePackLocation.fromString("eap@maven(org.jboss.eap.universe:eap8-universe)").getProducer();
    private static final ProducerSpec EAP_MAVEN_PRODUCER = FeaturePackLocation.fromString("org.jboss.eap:wildfly-ee-galleon-pack:").getProducer();

    /**
     * Whether to update the dependency on eap producer for producers (eg: eap-xp) that depend on it.
     * This parameter is meaningless if eap is present directly in the provisioning configuration.
     * This parameter is true by default. Set this parameter to false to disable the eap dependency update.
     */
    @Parameter(alias = "update-eap-dependency", defaultValue = "true", property = "eap.package.update.eap.dependency")
    boolean updateEapDependency;

    @Override
    protected void enrichRepositories() throws MojoExecutionException {
        // NO-OP
    }

    @Override
    protected void serverProvisioned(ProvisioningConfig config, Path jbossHome) throws MojoExecutionException, MojoFailureException {
        if (updateEapDependency) {
            try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                    .setInstallationHome(jbossHome)
                    .setMessageWriter(new MvnMessageWriter(getLog()))
                    .setLogTime(logProvisioningTime)
                    .setRecordState(recordProvisioningState)
                    .build()) {
                // In case EAP feature-pack is present in the config (transitive or not), do not upgrade the EAP version.
                // Provisioning will use the EAP feature-pack version (latest in channel or the specified one).
                boolean updateEAP = true;
                for (FeaturePackConfig fpconfig : config.getFeaturePackDeps()) {
                    getLog().debug("Producer in config: " + fpconfig.getLocation().getProducer());
                    if (EAP_PRODUCER.equals(fpconfig.getLocation().getProducer())
                            || EAP_MAVEN_PRODUCER.equals(fpconfig.getLocation().getProducer())) {
                        getLog().debug("EAP producer has been found in configuration");
                        updateEAP = false;
                    }
                }
                if (updateEAP) {
                    for (FeaturePackConfig fpconfig : config.getTransitiveDeps()) {
                        getLog().debug("Transitive Producer " + fpconfig.getLocation().getProducer());
                        if (EAP_PRODUCER.equals(fpconfig.getLocation().getProducer())
                                || EAP_MAVEN_PRODUCER.equals(fpconfig.getLocation().getProducer())) {
                            getLog().debug("EAP found as a transitive dependency");
                            updateEAP = false;
                        }
                    }
                }
                if (updateEAP) {
                    List<ChannelSpec> channelSpecs = new ArrayList<>();
                    try (ProvisioningRuntime rt = pm.getRuntime(config)) {

                        for (FeaturePackRuntime fprt : rt.getFeaturePacks()) {
                            for (FeaturePackConfig dep : fprt.getSpec().getFeaturePackDeps()) {
                                if (EAP_PRODUCER.equals(dep.getLocation().getProducer())) {
                                    getLog().debug(fprt.getFPID() + " has a dependency on eap " + dep.getLocation());
                                    channelSpecs.add(fprt.getFPID().getChannel());
                                    break;
                                }
                            }
                        }
                        if (!channelSpecs.isEmpty()) {
                            ProvisioningPlan plan = pm.getUpdates(EAP_PRODUCER);
                            getLog().debug("Checking for eap update");
                            if (!plan.isEmpty()) {
                                getLog().info("The eap depency is updated for " + channelSpecs);
                                getLog().info(plan.toString());
                                pm.apply(plan, galleonOptions);
                            }
                        }
                    }
                }
            } catch (ProvisioningException ex) {
                throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
            }
        }
        super.serverProvisioned(config, jbossHome);
    }
}
