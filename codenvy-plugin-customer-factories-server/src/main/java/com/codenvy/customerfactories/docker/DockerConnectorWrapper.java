package com.codenvy.customerfactories.docker;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.eclipse.che.plugin.docker.client.params.BuildImageParams;
import org.eclipse.che.plugin.docker.client.params.PushParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class DockerConnectorWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(DockerConnectorWrapper.class);
    private final DockerConnector dockerConnector;

    @Inject
    public DockerConnectorWrapper(final DockerConnector dockerConnector) {
        this.dockerConnector = dockerConnector;
    }

    /**
     * @param imageName
     * @param dockerfileContent
     * @return the id of the created Docker image
     * @throws ServerException
     */
    public String buildImageFromDockerfile(final String imageName, final String dockerfileContent) throws ServerException {
        final File dockerfile;
        try {
            dockerfile = Files.createTempFile("Dockerfile", "." + imageName).toFile();
            FileWriter writer = new FileWriter(dockerfile);
            writer.write(dockerfileContent);
            writer.close();
        } catch (IOException e) {
            throw new ServerException(e.getLocalizedMessage(), e);
        }

        final BuildImageParams buildParams = BuildImageParams.create(dockerfile)
                                                             .withRepository(imageName)
                                                             .withTag("latest");
        String imageId;
        try {
            imageId = dockerConnector.buildImage(buildParams, progressStatus -> {
                LOG.debug(progressStatus.getStatus());
            });
        } catch (IOException e) {
            throw new ServerException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerException(e.getLocalizedMessage(), e);
        }

        if (imageId == null) {
            throw new ServerException("A problem occurred during creation of the Docker image.");
        }

        return imageId;
    }

    /**
     * @param repositoryName
     * @throws ServerException
     */
    public void pushImage(String registryUrl, String repositoryName) throws ServerException {
        final PushParams pushParams = PushParams.create(repositoryName)
                                                .withRegistry(registryUrl);
        String digest;
        try {
            digest = dockerConnector.push(pushParams, progressStatus -> {
                LOG.debug(progressStatus.getStatus());
            });
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
}
