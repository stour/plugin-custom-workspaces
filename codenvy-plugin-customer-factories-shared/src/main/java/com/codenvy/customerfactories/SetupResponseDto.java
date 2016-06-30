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
