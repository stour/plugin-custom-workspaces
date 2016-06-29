package com.codenvy.customerfactories;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.eclipse.che.api.core.rest.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Api(
        value = "/customerfactories",
        description = "Service to setup custom Docker images and Codenvy factories"
)
@Path("/customerfactories")
public class CustomerFactoriesService extends Service {

  private static final Logger LOG = LoggerFactory.getLogger(CustomerFactoriesService.class);

  @Inject
  public CustomerFactoriesService() {

  }

  @POST
  @Consumes(APPLICATION_JSON)
  @ApiOperation(value = "Setup a new customer factory based on given parameters")
  @ApiResponses({@ApiResponse(code = 201, message = "The factory successfully created"),
                 @ApiResponse(code = 400, message = "Missed required parameters, parameters are not valid"),
                 @ApiResponse(code = 403, message = "The user does not have access to create a new factory"),
                 @ApiResponse(code = 409, message = ""),
                 @ApiResponse(code = 500, message = "Internal server error occurred")})
  public Response setup(@ApiParam(value = "", required = true) WorkspaceConfigDto config) {

  }
}
