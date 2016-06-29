package com.codenvy.customerfactories;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.google.common.collect.Lists;

import org.eclipse.che.api.auth.AuthenticationService;
import org.eclipse.che.api.auth.shared.dto.Credentials;
import org.eclipse.che.api.auth.shared.dto.Token;
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
import org.eclipse.che.api.machine.shared.dto.MachineConfigDto;
import org.eclipse.che.api.machine.shared.dto.MachineSourceDto;
import org.eclipse.che.api.workspace.shared.dto.EnvironmentDto;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceConfigDto;
import org.eclipse.che.api.factory.server.FactoryService;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.eclipse.che.plugin.docker.client.params.BuildImageParams;
import org.eclipse.che.plugin.docker.client.params.PushParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.UriBuilder.fromUri;

@Api(
        value = "/customerfactories",
        description = "Service to setup custom Docker images and Codenvy factories"
)
@Path("/customerfactories")
public class CustomerFactoriesService extends Service {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerFactoriesService.class);

    private static final String BASE_DOCKERFILE_URL =
            "https://gist.githubusercontent.com/stour/70d87f9edc9bf3d8edb9fd24d346acc9/raw/c16a6ce3b7f835bed40474f48fc8aa311852999e/Dockerfile";
    private static final String REGISTRY_URL        = "private.registry.org:5000";
    private static final String ADMIN_USERNAME      = "";
    private static final String ADMIN_PASSWORD      = "";

    private final String                 baseUrl;
    private final HttpJsonRequestFactory httpJsonRequestFactory;
    private final DockerConnector        dockerConnector;

    @Inject
    public CustomerFactoriesService(@Named("api.endpoint") String baseUrl, final HttpJsonRequestFactory httpJsonRequestFactory,
                                    final DockerConnector dockerConnector) {
        this.baseUrl = baseUrl;
        this.httpJsonRequestFactory = httpJsonRequestFactory;
        this.dockerConnector = dockerConnector;
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
                                  @ApiParam(value = "Credentials to connect to customer system", required = true) Credentials customerCredentials,
                                  @ApiParam(value = "List of all folders and files to copy", required = true) List<String> pathsToCopy,
                                  @ApiParam(value = "Name to use for the Docker image", required = true) String imageName,
                                  @ApiParam(value = "URL of the repository of the dev project", required = true) String repositoryUrl)
            throws ServerException {

        // Get base Dockerfile (common to all customers, embed utilities needed by Codenvy)
        final String baseDockerfileContent = getBaseDockerfile();

        // Add customer specific rysnc command(s) to Dockerfile
        // (using system URL, credentials and list of paths to copy)

        // Build Docker image based on newly created Dockerfile
        buildImage(imageName, baseDockerfileContent);

        // Push Docker image to the pre-configured registry
        pushImage(imageName);

        // Create a new Codenvy factory based on the Docker image and the repository of the dev project
        final String imageAbsoluteName = REGISTRY_URL + "/" + imageName;
        final Factory factory = createFactory(imageName, imageAbsoluteName, repositoryUrl);

        // Set current Codenvy user
        if (EnvironmentContext.getCurrent().getSubject() == null) {
            EnvironmentContext.getCurrent().setSubject(new TokenSubject());
        }

        // Save the new factory
        saveFactory(factory);

        return DtoFactory.newDto(SetupResponseDto.class)
                         .withImageAbsoluteName(imageAbsoluteName)
                         .withFactoryId(null);
    }

    private String getBaseDockerfile() throws ServerException {
        HttpJsonRequest request = httpJsonRequestFactory.fromUrl(BASE_DOCKERFILE_URL).useGetMethod();

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
            throw new ServerException("A problem occurred");
        }

        return response.asString();
    }

    /**
     * @param imageName
     * @param dockerfileContent
     * @return
     * @throws ServerException
     */
    private String buildImage(final String imageName, final String dockerfileContent) throws ServerException {
        final BuildImageParams buildParams = BuildImageParams.create(new File(dockerfilePath))
                                                             .withRepository(imageName)
                                                             .withTag("latest");
        String imageId;
        try {
            imageId = dockerConnector.buildImage(buildParams, null);
        } catch (IOException e) {
            throw new ServerException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerException(e.getLocalizedMessage(), e);
        }

        if (imageId == null) {
            throw new ServerException("A problem occurred during creation of the Docker image");
        }

        return imageId;
    }

    /**
     * @param repositoryName
     * @throws ServerException
     */
    private void pushImage(String repositoryName) throws ServerException {
        final PushParams pushParams = PushParams.create(repositoryName)
                                                .withRegistry(REGISTRY_URL);
        String digest;
        try {
            digest = dockerConnector.push(pushParams, null);
        } catch (IOException e) {
            throw new ServerException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerException(e.getLocalizedMessage(), e);
        }

        if (digest == null) {
            throw new ServerException("A problem occurred during pushing of the Docker image");
        }
    }

    private Factory createFactory(final String imageName, final String imageAbsoluteName, final String repositoryUrl) {
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
    private Factory saveFactory(final Factory factory) throws ServerException {
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

    /**
     * A user that only provides a token based on credentials configured in a property file
     */
    protected class TokenSubject implements Subject {

        private final Token token;

        public TokenSubject() throws ServerException {
            token = authenticateUser(ADMIN_USERNAME, ADMIN_PASSWORD);
        }

        @Override
        public String getUserName() {
            return "token-user";
        }

        @Override
        public boolean hasPermission(String domain, String instance, String action) {
            return false;
        }

        @Override
        public void checkPermission(String domain, String instance, String action) throws ForbiddenException {
            throw new ForbiddenException("User is not authorized to perform this operation");
        }

        @Override
        public String getToken() {
            return token.getValue();
        }

        @Override
        public String getUserId() {
            return "0000-00-0000";
        }

        @Override
        public boolean isTemporary() {
            return false;
        }

        /**
         * Authenticate against Codenvy
         *
         * @param username
         *         the username of the user to authenticate
         * @param password
         *         the password of the user to authenticate
         * @return an auth token if authentication is successful, null otherwise
         * @throws ServerException
         */
        private Token authenticateUser(String username, String password) throws ServerException {
            Token userToken;
            String url = fromUri(baseUrl).path(AuthenticationService.class).path(AuthenticationService.class, "authenticate")
                                         .build().toString();

            Credentials credentials = DtoFactory.newDto(Credentials.class).withUsername(username).withPassword(password);
            HttpJsonRequest httpJsonRequest = httpJsonRequestFactory.fromUrl(url).usePostMethod().setBody(credentials);
            try {
                HttpJsonResponse response = httpJsonRequest.request();
                userToken = response.asDto(Token.class);

            } catch (IOException | ApiException e) {
                LOG.error(e.getLocalizedMessage(), e);
                throw new ServerException(e.getLocalizedMessage());
            }
            if (userToken != null) {
                LOG.debug("successfully authenticated with token {}", userToken);
            }
            return userToken;
        }
    }
}
