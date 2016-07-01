/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package com.codenvy.customerfactories;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.codenvy.customerfactories.docker.DockerConnectorWrapper;
import com.codenvy.customerfactories.docker.DockerRecipe;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.factory.shared.dto.Factory;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static com.codenvy.customerfactories.docker.DockerRecipe.INSTR_POSITION.BEFORE_CMD;

@Api(
        value = "/customerfactories",
        description = "Service to setup custom Docker images and Codenvy factories"
)
@Path("/customerfactories")
public class CustomerFactoriesService extends Service {

    private static final Logger LOG                       = LoggerFactory.getLogger(CustomerFactoriesService.class);
    private static final String BASE_DOCKERFILE_URL       = "https://gist.githubusercontent.com/" +
                                                            "stour/70d87f9edc9bf3d8edb9fd24d346acc9/" +
                                                            "raw/c16a6ce3b7f835bed40474f48fc8aa311852999e/Dockerfile";
    private static final String RSYNC_INSTRUCTION_PATTERN = "rsync -avz -e \"ssh -i %s\" %s@%s:%s %s";
    private static final String REGISTRY_URL              = "localhost:5000";

    private final FactoryConnector       factoryConnector;
    private final DockerConnectorWrapper dockerConnectorWrapper;

    @Inject
    public CustomerFactoriesService(final FactoryConnector factoryConnector, final DockerConnectorWrapper dockerConnectorWrapper) {
        this.factoryConnector = factoryConnector;
        this.dockerConnectorWrapper = dockerConnectorWrapper;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Setup a new customer factory based on given parameters")
    @ApiResponses({@ApiResponse(code = 201, message = "The factory successfully created"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 403, message = "The user does not have access to create a new factory"),
                   @ApiResponse(code = 409, message = ""),
                   @ApiResponse(code = 500, message = "Internal server error occurred")})
    public SetupResponseDto setup(@ApiParam(value = "URL of customer system", required = true) String customerUrl,
                                  @ApiParam(value = "User to connect to customer system", required = true) String customerUser,
                                  @ApiParam(value = "Public key to connect to customer system", required = true) String customerPublicKey,
                                  @ApiParam(value = "List of all folders and files to copy", required = true) List<String> pathsToCopy,
                                  @ApiParam(value = "Name to use for the Docker image", required = true) String imageName,
                                  @ApiParam(value = "URL of the repository of the dev project", required = true) String repositoryUrl)
            throws ServerException {

        // Get base Dockerfile (common to all customers, embed utilities needed by Codenvy)
        final Link recipeLink = DtoFactory.newDto(Link.class)
                                          .withHref(BASE_DOCKERFILE_URL)
                                          .withMethod("GET");
        final DockerRecipe recipe = new DockerRecipe(recipeLink);

        // Store customer public key in a temp file
        File publicKeyFile;
        try {
            publicKeyFile = Files.createTempFile(imageName, ".pub").toFile();
            FileWriter writer = new FileWriter(publicKeyFile);
            writer.write(customerPublicKey);
            writer.close();
        } catch (IOException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
        final String publicKeyFilePath = publicKeyFile.getPath();

        // Add customer specific rysnc commands to Dockerfile
        String instruction = "";
        for (int i = 0; i < pathsToCopy.size(); i++) {
            final String path = pathsToCopy.get(0);
            final String rsyncInstr = String.format(RSYNC_INSTRUCTION_PATTERN, publicKeyFilePath, customerUser, customerUrl, path, path);
            if (i == 0) {
                instruction += "RUN " + rsyncInstr + " && \\ \n";
            } else if (i == pathsToCopy.size()) {
                instruction += "    " + rsyncInstr;
            } else {
                instruction += "    " + rsyncInstr + " && \\ \n";
            }
        }
        recipe.addInstruction(instruction, BEFORE_CMD);

        // Build Docker image based on newly created Dockerfile and file containing the customer public key
        dockerConnectorWrapper.buildImageFromDockerfile(imageName, recipe.getContent(), publicKeyFile);

        // Push Docker image to the pre-configured registry
        dockerConnectorWrapper.pushImage(REGISTRY_URL, imageName);

        // Create a new Codenvy factory based on the Docker image and the repository of the dev project
        final String imageAbsoluteName = REGISTRY_URL + "/" + imageName;
        final Factory factory = factoryConnector.createFactory(imageName, imageAbsoluteName, repositoryUrl);

        // Save the new factory
        factoryConnector.saveFactory(factory);

        return DtoFactory.newDto(SetupResponseDto.class)
                         .withImageAbsoluteName(imageAbsoluteName)
                         .withFactoryId(null);
    }
}
