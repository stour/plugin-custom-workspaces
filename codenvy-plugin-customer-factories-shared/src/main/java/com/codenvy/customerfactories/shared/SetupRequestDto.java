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
package com.codenvy.customerfactories.shared;

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

    /**
     * URL of the Docker registry to use
     *
     */
    String getRegistryUrl();

    void setRegistryUrl(String registryUrl);

    SetupRequestDto withRegistryUrl(String registryUrl);
}
