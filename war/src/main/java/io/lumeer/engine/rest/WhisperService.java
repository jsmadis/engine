/*
 * Lumeer: Modern Data Definition and Processing Platform
 *
 * Copyright (C) since 2017 Answer Institute, s.r.o. and/or its affiliates.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.lumeer.engine.rest;

import io.lumeer.engine.annotation.UserDataStorage;
import io.lumeer.engine.api.LumeerConst;
import io.lumeer.engine.api.constraint.ConstraintManager;
import io.lumeer.engine.api.data.DataStorage;
import io.lumeer.engine.api.exception.CollectionNotFoundException;
import io.lumeer.engine.controller.CollectionFacade;
import io.lumeer.engine.controller.CollectionMetadataFacade;
import io.lumeer.engine.controller.ConfigurationFacade;
import io.lumeer.engine.controller.OrganizationFacade;
import io.lumeer.engine.controller.ProjectFacade;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * Whispers to user any possibilities that can be entered as input.
 *
 * @author <a href="mailto:marvenec@gmail.com">Martin Večeřa</a>
 */
@Path("/organizations/{organization}/projects/{project}/whisper")
public class WhisperService {

   @Inject
   @UserDataStorage
   private DataStorage dataStorage;

   @Inject
   private CollectionFacade collectionFacade;

   @Inject
   private CollectionMetadataFacade collectionMetadataFacade;

   @Inject
   @Named("systemConstraintManager")
   private ConstraintManager constraintManager;

   @Inject
   private ConfigurationFacade configurationFacade;

   private Locale locale = Locale.getDefault();

   @PathParam("organization")
   private String organisationCode;

   @PathParam("project")
   private String projectCode;

   @Inject
   private OrganizationFacade organizationFacade;

   @Inject
   private ProjectFacade projectFacade;

   @PostConstruct
   public void init() {
      organizationFacade.setOrganizationCode(organisationCode);
      projectFacade.setCurrentProjectCode(projectCode);

      locale = Locale.forLanguageTag(configurationFacade.getConfigurationString(LumeerConst.USER_LOCALE_PROPERTY).orElse("en-US"));
   }

   @GET
   @Path("/collection")
   @Produces(MediaType.APPLICATION_JSON)
   public Set<String> getPossibleCollectionNames(@QueryParam("collectionCode") final String collectionName) {
      if (collectionName == null || collectionName.isEmpty()) {
         return collectionMetadataFacade.getCollectionsCodeName().values().stream().collect(Collectors.toSet());
      } else {
         return collectionMetadataFacade.getCollectionsCodeName().values().stream().filter(name -> name.toLowerCase(locale).startsWith(collectionName.toLowerCase(locale))).collect(Collectors.toSet());
      }
   }

   @GET
   @Path("/collection/{collectionCode}")
   @Produces(MediaType.APPLICATION_JSON)
   public Set<String> getPossibleCollectionAttributeNames(@PathParam("collectionCode") final String collectionCode, @QueryParam("attributeName") final String attributeName) throws CollectionNotFoundException {
      // returns empty set if user collection name does not exists
      if (collectionCode == null) {
         throw new BadRequestException();
      }

      if (attributeName == null || attributeName.isEmpty()) {
         return collectionMetadataFacade.getAttributesNames(collectionCode).stream().collect(Collectors.toSet());
      } else {
         return collectionMetadataFacade.getAttributesNames(collectionCode)
                                        .stream()
                                        .filter(name -> name.toLowerCase(locale)
                                                            .startsWith(attributeName.toLowerCase(locale)))
                                        .collect(Collectors.toSet());
      }

   }

   /**
    * Gets available names of constraint prefixes.
    *
    * @param constraintName
    *       Already written part of the constraint prefix.
    * @return Set of available constraint prefix names according to the already entered part.
    */
   @GET
   @Path("/constraint")
   @Produces(MediaType.APPLICATION_JSON)
   public Set<String> getPossibleConstraintNamePrefixes(@QueryParam("constraintName") final String constraintName) {
      if (constraintName != null && !constraintName.isEmpty()) {
         return constraintManager.getRegisteredPrefixes().stream().filter(prefix -> prefix.toLowerCase(locale).startsWith(constraintName.toLowerCase(locale))).collect(Collectors.toSet());
      }

      return constraintManager.getRegisteredPrefixes();
   }

   /**
    * Gets available parameter values for the given constraint.
    *
    * @param constraintName
    *       Name of the constraint.
    * @param constraintParam
    *       Already written part of the constraint parameter.
    * @return Set of available constraint parameters based on the already entered part.
    */
   @GET
   @Path("/constraint/{constraintName}")
   @Produces(MediaType.APPLICATION_JSON)
   public Set<String> getPossibleConstraintNameParameters(@PathParam("constraintName") final String constraintName, @QueryParam("constraintParam") final String constraintParam) {
      if (constraintParam != null && !constraintParam.isEmpty()) {
         return constraintManager.getConstraintParameterSuggestions(constraintName).stream().filter(
               suggestion -> suggestion.toLowerCase(locale).startsWith(constraintParam.toLowerCase(locale))).collect(Collectors.toSet());
      }

      return constraintManager.getConstraintParameterSuggestions(constraintName);
   }

}
