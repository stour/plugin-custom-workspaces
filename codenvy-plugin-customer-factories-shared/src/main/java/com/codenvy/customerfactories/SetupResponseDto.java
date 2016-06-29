package com.codenvy.customerfactories;

import org.eclipse.che.dto.shared.DTO;

@DTO
public interface SetupResponseDto {
    String getImageAbsoluteName();

    void setImageAbsoluteName(String imageAbsoluteName);

    SetupResponseDto withImageAbsoluteName(String imageAbsoluteName);

    String getImageId();

    void setImageId(String imageId);

    SetupResponseDto withImageId(String imageId);

    String getFactoryId();

    void setFactoryId(String factoryId);

    SetupResponseDto withFactoryId(String factoryId);
}
