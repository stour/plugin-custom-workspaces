package com.codenvy.customerfactories.docker;

import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.core.rest.shared.dto.Link;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;

public class DockerRecipe {

    public enum INSTR_POSITION {
        FIRST,
        LAST,
        BEFORE_CMD
    }

    @Inject
    private HttpJsonRequestFactory httpJsonRequestFactory;

    private String content;

    public DockerRecipe(String content) {
        this.content = content;
    }

    public DockerRecipe(@NotNull Link link) throws ServerException {
        final String url = link.getHref();
        final String method = link.getMethod();

        HttpJsonRequest request = httpJsonRequestFactory.fromUrl(url).setMethod(method);

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

        content = response.asString();
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void addInstruction(String instruction, INSTR_POSITION position) {

    }
}
