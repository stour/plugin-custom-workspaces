/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
            throw new ServerException("A problem occurred while downloading recipe.");
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
        final List<String> lines = Arrays.asList(content.split("\n"));

        switch (position) {
            case FIRST:
                // insert a new line right after FROM line
                int j;
                for (j = 0; j < lines.size(); j++) {
                    if (lines.get(j).startsWith("FROM")) {
                        break;
                    }
                }
                lines.add(j + 1, instruction);
                lines.add(j + 2, "\n");
                content = lines.stream().collect(Collectors.joining("\n"));
                break;
            case LAST:
                // insert a new line at the end of the file
                lines.add(instruction);
                lines.add("\n");
                content = lines.stream().collect(Collectors.joining("\n"));
                break;
            case BEFORE_CMD:
                // if Dockerfile contains a CMD line then insert a new line right before
                int i;
                for (i = 0; i < lines.size(); i++) {
                    if (lines.get(i).startsWith("CMD")) {
                        break;
                    }
                }
                if (i > 0) {
                    lines.add(i, instruction);
                    lines.add(i + 1, "\n");
                    // otherwise insert a new line at the end of the file
                } else {
                    lines.add(instruction);
                    lines.add("\n");
                }
                content = lines.stream().collect(Collectors.joining("\n"));
                break;
            default:
        }
    }
}
