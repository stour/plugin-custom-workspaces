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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DockerRecipe {

    private String content;

    public DockerRecipe(final String content) {
        this.content = content;
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
                boolean instructionFromFound = false;
                int j;
                for (j = 0; j < lines.size(); j++) {
                    final String line = lines.get(j).trim();
                    if (line.startsWith("FROM")) {
                        instructionFromFound = true;

                    }
                    if (instructionFromFound) {
                        if (!line.endsWith("\\")) {
                            break;
                        }
                    }
                }
                if (instructionFromFound) {
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
                boolean instructionCmdFound = false;
                int i;
                for (i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i).trim();
                    if (line.startsWith("CMD")) {
                        instructionCmdFound = true;
                    }
                    if (instructionCmdFound) {
                        if (!line.endsWith("\\")) {
                            break;
                        }
                    }
                }
                if (instructionCmdFound) {
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
