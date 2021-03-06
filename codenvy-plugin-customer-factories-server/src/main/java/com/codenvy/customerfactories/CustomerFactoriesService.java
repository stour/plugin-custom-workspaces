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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.codenvy.customerfactories.docker.DockerConnectorWrapper;
import com.codenvy.customerfactories.docker.DockerRecipe;
import com.codenvy.customerfactories.shared.SetupRequestDto;
import com.codenvy.customerfactories.shared.SetupResponseDto;

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.factory.shared.dto.Factory;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.codenvy.customerfactories.docker.InstructionPosition.BEFORE_CMD;
import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Api(
        value = "/customerfactories",
        description = "Service to setup custom Docker images and Codenvy factories"
)
@Path("/customerfactories")
public class CustomerFactoriesService extends Service {

    private static final Logger LOG                         = LoggerFactory.getLogger(CustomerFactoriesService.class);
    private static final String BASE_DOCKERFILE_URL         = "https://gist.githubusercontent.com/" +
                                                              "stour/70d87f9edc9bf3d8edb9fd24d346acc9/" +
                                                              "raw/c16a6ce3b7f835bed40474f48fc8aa311852999e/Dockerfile";
    private static final String RSYNC_INSTRUCTION_PATTERN   = "rsync -avz -e \"ssh -i %s\" %s@%s:%s %s";
    private static final String BASE_DOCKERFILE_USER_FOLDER = "/home/user/";

    private final FactoryConnector       factoryConnector;
    private final DockerConnectorWrapper dockerConnectorWrapper;
    private final HttpJsonRequestFactory httpJsonRequestFactory;

    @Inject
    public CustomerFactoriesService(final FactoryConnector factoryConnector, final DockerConnectorWrapper dockerConnectorWrapper,
                                    final HttpJsonRequestFactory httpJsonRequestFactory) {
        this.factoryConnector = factoryConnector;
        this.dockerConnectorWrapper = dockerConnectorWrapper;
        this.httpJsonRequestFactory = httpJsonRequestFactory;
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Setup a new customer factory based on given parameters",
                  response = SetupResponseDto.class)
    @ApiResponses({@ApiResponse(code = 201, message = "Factory successfully created"),
                   @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                   @ApiResponse(code = 500, message = "Internal server error occurred")})
    public SetupResponseDto setup(
            @ApiParam(value = "The configuration to create the new customer factory", required = true) SetupRequestDto setupRequestDto)
            throws ApiException {

        if (setupRequestDto == null || setupRequestDto.getPathsToCopy() == null || setupRequestDto.getPathsToCopy().size() == 0 ||
            isNullOrEmpty(setupRequestDto.getCustomerUser()) || isNullOrEmpty(setupRequestDto.getCustomerUrl()) ||
            isNullOrEmpty(setupRequestDto.getCustomerPublicKey()) || isNullOrEmpty(setupRequestDto.getRepositoryUrl()) ||
            isNullOrEmpty(setupRequestDto.getImageName()) || isNullOrEmpty(setupRequestDto.getRegistryUrl())) {

            throw new BadRequestException("All setup request fields are mandatory.");
        }

        final String customerUser = setupRequestDto.getCustomerUser();
        final String customerUrl = setupRequestDto.getCustomerUrl();
        final String customerPublicKey = setupRequestDto.getCustomerPublicKey();
        final String repositoryUrl = setupRequestDto.getRepositoryUrl();
        final String imageName = setupRequestDto.getImageName();
        final List<String> pathsToCopy = setupRequestDto.getPathsToCopy();
        final String registryUrl = setupRequestDto.getRegistryUrl();

        // Get base Dockerfile (common to all customers, embed utilities needed by Codenvy)
        String content;
        try {
            content = resolveRecipeContent(new URL(BASE_DOCKERFILE_URL));
        } catch (MalformedURLException e) {
            throw new ServerException(e.getLocalizedMessage());
        }

        final DockerRecipe recipe = new DockerRecipe(content);

        // Store customer public key in a temp file
        final File publicKeyFile = createTempFile(imageName, ".pub", customerPublicKey);

        // Update Dockerfile to COPY public key file
        final String publicKeyFileName = publicKeyFile.getName();
        final String publicKeyImagePath = BASE_DOCKERFILE_USER_FOLDER + publicKeyFileName;
        recipe.addCopyInstruction(publicKeyFileName, publicKeyImagePath, BEFORE_CMD);

        // Update Dockerfile to RUN rsync commands
        List<String> rsyncCommands = new ArrayList<>();
        for (String path : pathsToCopy) {
            final String rsyncCommand = String.format(RSYNC_INSTRUCTION_PATTERN, publicKeyImagePath, customerUser, customerUrl, path, path);
            rsyncCommands.add(rsyncCommand);
        }
        recipe.addRunInstruction(rsyncCommands, BEFORE_CMD);

        // Build Docker image based on the updated Dockerfile
        final File dockerfileFile = createTempFile("Dockerfile", "", recipe.getContent());
        final String imageId = dockerConnectorWrapper.buildImage(imageName, dockerfileFile, publicKeyFile);

        // Push Docker image to pre-configured registry
        final String pushDigest = dockerConnectorWrapper.pushImage(registryUrl, imageName);

        // Create a new Codenvy factory based on the Docker image and the repository of the dev project
        final String imageAbsoluteName = registryUrl + "/" + imageName;
        final Factory factory = factoryConnector.createFactory(imageName, imageAbsoluteName, repositoryUrl);

        // Save the new factory
        final Factory savedFactory = factoryConnector.saveFactory(factory);

        return DtoFactory.newDto(SetupResponseDto.class)
                         .withImageAbsoluteName(imageAbsoluteName)
                         .withImageId(imageId)
                         .withFactoryId(savedFactory.getId());
    }

    private String resolveRecipeContent(URL url) throws ServerException {
        final HttpJsonRequest request = httpJsonRequestFactory.fromUrl(url.toExternalForm()).setMethod("GET");

        HttpJsonResponse response;
        try {
            response = request.request();
        } catch (IOException e) {
            throw new ServerException(e);
        } catch (UnauthorizedException e) {
            throw new ServerException(e);
        } catch (ForbiddenException e) {
            throw new ServerException(e);
        } catch (NotFoundException e) {
            throw new ServerException(e);
        } catch (ConflictException e) {
            throw new ServerException(e);
        } catch (BadRequestException e) {
            throw new ServerException(e);
        }

        if (response == null) {
            throw new ServerException("A problem occurred while downloading recipe " + url + ".");
        }

        return response.asString();
    }

    private File createTempFile(final String prefix, final String suffix, final String content) throws ServerException {
        File tempFile;
        try {
            tempFile = Files.createTempFile(prefix, suffix).toFile();
            FileWriter writer = new FileWriter(tempFile);
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }
        return tempFile;
    }
}
