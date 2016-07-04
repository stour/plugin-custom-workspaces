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

    /**
     *
     * @param sourcePath
     * @param destinationPath
     * @param position
     */
    public void addCopyInstruction(final String sourcePath, final String destinationPath, InstructionPosition position) {
        addInstruction("COPY " + sourcePath + " " + destinationPath, position);
    }

    /**
     *
     * @param command
     * @param position
     */
    public void addRunInstruction(final String command, InstructionPosition position) {
        addInstruction("RUN " + command, position);
    }

    /**
     *
     * @param commands
     * @param position
     */
    public void addRunInstruction(final List<String> commands, InstructionPosition position) {
        String concatenatedCommand = "";
        for (int i = 0; i < commands.size(); i++) {
            final String command = commands.get(0);
            if (i == commands.size() - 1) {
                concatenatedCommand += command;
            } else {
                concatenatedCommand += command + " && ";
            }
        }
        addRunInstruction(concatenatedCommand, position);
    }

    private void addInstruction(String instruction, InstructionPosition position) {
        final List<String> lines = Arrays.asList(content.split("\n"));

        switch (position) {
            case FIRST:
                // insert a new line right after FROM line
                boolean containsFrom = false;
                int j;
                for (j = 0; j < lines.size(); j++) {
                    final String line = lines.get(j).trim();
                    if (containsFrom) {
                        if (!line.endsWith("\\")) {
                            break;
                        }
                    } else if (line.startsWith("FROM")) {
                        if (!line.endsWith("\\")) {
                            break;
                        } else {
                            containsFrom = true;
                        }
                    }
                }
                if (containsFrom) {
                    lines.add(j + 1, instruction);
                    lines.add(j + 2, "\n");
                } else {
                    lines.add(0, instruction);
                    lines.add(1, "\n");
                }
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
                boolean containsCmd = false;
                int i;
                for (i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i).trim();
                    if (containsCmd) {
                        if (!line.endsWith("\\")) {
                            break;
                        }
                    } else if (line.startsWith("CMD")) {
                        if (!line.endsWith("\\")) {
                            break;
                        } else {
                            containsCmd = true;
                        }
                    }
                }
                if (containsCmd) {
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
