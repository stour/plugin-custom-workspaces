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

import org.eclipse.che.dto.shared.DTO;

import java.util.List;

@DTO
public interface SetupRequestDto {
    /**
     * URL of customer system
     *
     */
    String getCustomerUrl();

    void setCustomerUrl(String customerUrl);

    SetupRequestDto withCustomerUrl(String customerUrl);

    /**
     * User to connect to customer system
     *
     */
    String getCustomerUser();

    void setCustomerUser(String customerUser);

    SetupRequestDto withCustomerUser(String customerUser);

    /**
     * Public key to connect to customer system
     *
     */
    String getCustomerPublicKey();

    void setCustomerPublicKey(String customerPublicKey);

    SetupRequestDto withCustomerPublicKey(String customerPublicKey);

    /**
     * List of all folders and files to copy
     *
     */
    List<String> getPathsToCopy();

    void setPathsToCopy(List<String> pathsToCopy);

    SetupRequestDto withPathsToCopy(List<String> pathsToCopy);

    /**
     * Name to use for the Docker image
     *
     */
    String getImageName();

    void setImageName(String imageName);

    SetupRequestDto withImageName(String imageName);

    /**
     * URL of the repository of the customer dev project
     *
     */
    String getRepositoryUrl();

    void setRepositoryUrl(String repositoryUrl);

    SetupRequestDto withRepositoryUrl(String repositoryUrl);
}
