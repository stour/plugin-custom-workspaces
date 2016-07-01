/*
 *  [2012] - [2016] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.customerfactories;

import com.google.common.collect.Lists;

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.factory.server.FactoryService;
import org.eclipse.che.api.factory.shared.dto.Factory;
import org.eclipse.che.api.machine.shared.dto.MachineConfigDto;
import org.eclipse.che.api.machine.shared.dto.MachineSourceDto;
import org.eclipse.che.api.workspace.shared.dto.EnvironmentDto;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceConfigDto;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;

import static javax.ws.rs.core.UriBuilder.fromUri;

public class FactoryConnector {

    private static final Logger LOG = LoggerFactory.getLogger(FactoryConnector.class);

    private final String                 baseUrl;
    private final HttpJsonRequestFactory httpJsonRequestFactory;

    @Inject
    public FactoryConnector(@Named("api.endpoint") String baseUrl, HttpJsonRequestFactory httpJsonRequestFactory) {
        this.baseUrl = baseUrl;
        this.httpJsonRequestFactory = httpJsonRequestFactory;
    }

    /**
     * @param imageName
     * @param imageAbsoluteName
     * @param repositoryUrl
     * @return
     */
    public Factory createFactory(final String imageName, final String imageAbsoluteName, final String repositoryUrl) {

        final SourceStorageDto projectSource = DtoFactory.newDto(SourceStorageDto.class)
                                                         .withType("git")
                                                         .withLocation(repositoryUrl);
        final ProjectConfigDto project = DtoFactory.newDto(ProjectConfigDto.class)
                                                   .withSource(projectSource);

        final MachineSourceDto machineSource = DtoFactory.newDto(MachineSourceDto.class)
                                                         .withType("dockerfile")
                                                         .withContent("FROM " + imageAbsoluteName);
        final MachineConfigDto machineConfig = DtoFactory.newDto(MachineConfigDto.class)
                                                         .withDev(true)
                                                         .withType("docker")
                                                         .withSource(machineSource);
        final EnvironmentDto environment = DtoFactory.newDto(EnvironmentDto.class)
                                                     .withMachineConfigs(Lists.newArrayList(machineConfig));

        final WorkspaceConfigDto workspace = DtoFactory.newDto(WorkspaceConfigDto.class)
                                                       .withProjects(Lists.newArrayList(project))
                                                       .withEnvironments(Lists.newArrayList(environment));
        return DtoFactory.newDto(Factory.class)
                         .withName("factory-" + imageName)
                         .withWorkspace(workspace);
    }

    /**
     * Save a new factory
     *
     * @param factory
     *         the factory to create
     * @return the created factory or null if an error occurred during the call to 'saveFactory'
     * @throws ServerException
     */
    public Factory saveFactory(final Factory factory) throws ServerException {

        // Test if there is an authenticated user in current context
        if (EnvironmentContext.getCurrent().getSubject() == null) {
            throw new ServerException("You need to authenticate on Codenvy before calling this service.");
        }

        final String url = fromUri(baseUrl).path(FactoryService.class).build().toString();
        Factory newFactory;
        HttpJsonRequest httpJsonRequest = httpJsonRequestFactory.fromUrl(url)
                                                                .usePostMethod()
                                                                .setBody(factory);
        try {
            HttpJsonResponse response = httpJsonRequest.request();
            newFactory = response.asDto(Factory.class);

        } catch (IOException | ApiException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new ServerException(e.getLocalizedMessage());
        }
        return newFactory;
    }
}
