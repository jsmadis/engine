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
package io.lumeer.engine.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.lumeer.engine.IntegrationTestBase;
import io.lumeer.engine.annotation.UserDataStorage;
import io.lumeer.engine.api.LumeerConst;
import io.lumeer.engine.api.data.DataDocument;
import io.lumeer.engine.api.data.DataStorage;
import io.lumeer.engine.api.data.DataStorageDialect;
import io.lumeer.engine.api.exception.VersionUpdateConflictException;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

/**
 * @author <a href="mailto:kotrady.johnny@gmail.com>Jan Kotrady</a>
 */
@RunWith(Arquillian.class)
public class VersionFacadeIntegrationTest extends IntegrationTestBase {

   private final String VERSION_STRING = LumeerConst.Document.METADATA_VERSION_KEY;
   private final String TEST_READ_VERSION = "versionTestReadVersion";
   private final String TEST_NEW_VERSION = "versionTestNewVersion";
   private final String TEST_NEW_VERSION_N = "versionTestNewVersionNoMeta";
   private final String TEST_CHANGED = "versionTestChangedDocumentTest";
   private final String TEST_MULTIPLE_VERSION = "versionTestMultipleVersion";
   private final String TEST_GET_OLD_DOC = "versionTestGetOldDocuments";
   private final String TEST_REVERT = "versionTestRevert";
   private final String TEST_EXCEPTION = "versionTestException";

   @Inject
   private VersionFacade versionFacade;

   @Inject
   @UserDataStorage
   private DataStorage dataStorage;

   @Inject
   private DataStorageDialect dataStorageDialect;

   @Inject
   private CollectionFacade collectionFacade;

   @Test
   public void testGetVersion() throws Exception {
      createCollection(TEST_READ_VERSION);
      DataDocument dataDocument = createTestDocument();
      String id = dataStorage.createDocument(TEST_READ_VERSION, dataDocument);
      assertThat(versionFacade.getDocumentVersion(dataStorage.readDocument(TEST_READ_VERSION, dataStorageDialect.documentIdFilter(id)))).isEqualTo(1);
   }

   @Test
   public void testGetVersionFromDocument() throws Exception {
      DataDocument dataDocument = createTestDocument();
      assertThat(versionFacade.getDocumentVersion(dataDocument)).isEqualTo(1);
   }

   @Test
   public void testNewVersion() throws Exception {
      String shadow = createCollection(TEST_NEW_VERSION);
      DataDocument dataDocument = createTestDocument();
      String documentId = dataStorage.createDocument(TEST_NEW_VERSION, dataDocument);
      dataDocument.put("dog", "dog");
      dataDocument.setId(documentId);
      versionFacade.newDocumentVersion(TEST_NEW_VERSION, dataStorage.readDocument(TEST_NEW_VERSION, dataStorageDialect.documentIdFilter(documentId)), dataDocument, false);
      assertThat(dataStorage.readDocument(TEST_NEW_VERSION, dataStorageDialect.documentIdFilter(documentId)).getInteger(VERSION_STRING).intValue()).isEqualTo(2);
      assertThat(versionFacade.getDocumentVersion(dataStorage.readDocument(shadow, dataStorageDialect.documentNestedIdFilterWithVersion(documentId, 1)))).isEqualTo(1);
      assertThat(dataStorage.readDocument(shadow, dataStorageDialect.documentNestedIdFilterWithVersion(documentId, 1)).getString("dog")).isEqualTo("cat");
   }

   @Test
   public void testWithoutMeta() throws Exception {
      String shadow = createCollection(TEST_NEW_VERSION_N);
      DataDocument dataDocument = createEmptyDocument();
      String documentId = dataStorage.createDocument(TEST_NEW_VERSION_N, dataDocument);
      dataDocument.setId(documentId);
      versionFacade.newDocumentVersion(TEST_NEW_VERSION_N, dataStorage.readDocument(TEST_NEW_VERSION_N, dataStorageDialect.documentIdFilter(documentId)), dataDocument, false);

      assertThat(dataStorage.readDocument(TEST_NEW_VERSION_N, dataStorageDialect.documentIdFilter(documentId)).getInteger(VERSION_STRING).intValue()).isEqualTo(1);
      assertThat(versionFacade.getDocumentVersion(dataStorage.readDocument(shadow, dataStorageDialect.documentNestedIdFilterWithVersion(documentId, 0)))).isEqualTo(0);
   }

   @Test
   public void testChangedDocument() throws Exception {
      String shadow = createCollection(TEST_CHANGED);
      DataDocument dataDocument = createTestDocument();
      String documentId = dataStorage.createDocument(TEST_CHANGED, dataDocument);
      dataDocument.setId(documentId);
      dataDocument.replace("dog", "pig");
      versionFacade.newDocumentVersion(TEST_CHANGED, dataStorage.readDocument(TEST_CHANGED, dataStorageDialect.documentIdFilter(documentId)), dataDocument, false);

      DataDocument fromDb = dataStorage.readDocument(TEST_CHANGED, dataStorageDialect.documentIdFilter(documentId));
      assertThat(fromDb.getInteger(VERSION_STRING).intValue()).isEqualTo(2);
      assertThat(fromDb.getString("dog")).isEqualTo("pig");
      DataDocument oldDoc = dataStorage.readDocument(shadow, dataStorageDialect.documentNestedIdFilterWithVersion(documentId, 1));
      assertThat(oldDoc.getInteger(VERSION_STRING).intValue()).isEqualTo(1);
      assertThat(oldDoc.getString("dog")).isEqualTo("cat");
   }

   @Test
   public void testGetVersions() throws Exception {
      String shadow = createCollection(TEST_MULTIPLE_VERSION);
      DataDocument dataDocument = createTestDocument();
      String documentId = dataStorage.createDocument(TEST_MULTIPLE_VERSION, dataDocument);
      dataDocument.setId(documentId);
      DataDocument actual;
      for (int i = 1; i < 10; i++) {
         actual = dataStorage.readDocument(TEST_MULTIPLE_VERSION, dataStorageDialect.documentIdFilter(documentId));
         versionFacade.newDocumentVersion(TEST_MULTIPLE_VERSION, actual, dataDocument, false);
      }
      assertThat(versionFacade.getDocumentVersions(TEST_MULTIPLE_VERSION, documentId)).hasSize(10);
      dataStorage.dropDocument(shadow, dataStorageDialect.documentNestedIdFilterWithVersion(documentId, 1));
      assertThat(versionFacade.getDocumentVersions(TEST_MULTIPLE_VERSION, documentId)).hasSize(9);
   }

   @Test
   public void getOldDocument() throws Exception {
      String shadow = createCollection(TEST_GET_OLD_DOC);
      DataDocument dataDocument = createTestDocument();
      String documentId = dataStorage.createDocument(TEST_GET_OLD_DOC, dataDocument);
      DataDocument actual = dataStorage.readDocument(TEST_GET_OLD_DOC, dataStorageDialect.documentIdFilter(documentId));
      dataDocument.setId(documentId);
      versionFacade.newDocumentVersion(TEST_GET_OLD_DOC, actual, dataDocument, false);
      DataDocument testDocument = versionFacade.readOldDocumentVersion(TEST_GET_OLD_DOC, dataDocument, 1);
      dataDocument = dataStorage.readDocument(TEST_GET_OLD_DOC, dataStorageDialect.documentIdFilter(documentId));
      testDocument.replace(VERSION_STRING, 2);
      assertThat(testForEquiv(testDocument, dataDocument)).isTrue();
   }

   @Test
   public void testNotVersion() throws Exception {
      assertThat(versionFacade.getDocumentVersion(new DataDocument())).isEqualTo(0);
   }

   @Test
   public void testChangedDocumentRevert() throws Exception {
      String shadow = createCollection(TEST_REVERT);
      DataDocument dataDocument = createTestDocument();
      String documentId = dataStorage.createDocument(TEST_REVERT, dataDocument);
      DataDocument actual = dataStorage.readDocument(TEST_REVERT, dataStorageDialect.documentIdFilter(documentId));
      dataDocument = new DataDocument(actual);
      dataDocument.replace("dog", "pig");
      versionFacade.newDocumentVersion(TEST_REVERT, actual, dataDocument, false);
      DataDocument oldDoc = versionFacade.readOldDocumentVersion(TEST_REVERT, documentId, 1);
      versionFacade.revertDocumentVersion(TEST_REVERT, dataDocument, oldDoc);
      DataDocument newDoc = dataStorage.readDocument(TEST_REVERT, dataStorageDialect.documentIdFilter(documentId));
      assertThat(versionFacade.getDocumentVersion(newDoc)).isEqualTo(3);
      assertThat(newDoc.getString("dog")).isEqualTo("cat");
      assertThat(dataStorage.readDocument(shadow, dataStorageDialect.documentNestedIdFilterWithVersion(documentId, 2)).getString("dog")).isEqualTo("pig");
   }

   @Test(expected = VersionUpdateConflictException.class)
   public void testExceptionUpdateDouble() throws Exception {
      createCollection(TEST_EXCEPTION);
      DataDocument dataDocument = createTestDocument();
      String documentId = dataStorage.createDocument(TEST_EXCEPTION, dataDocument);
      dataDocument = dataStorage.readDocument(TEST_EXCEPTION, dataStorageDialect.documentIdFilter(documentId));
      versionFacade.backUpDocument(TEST_EXCEPTION, dataDocument);
      versionFacade.backUpDocument(TEST_EXCEPTION, dataDocument);
   }

   /* @Test
    public void testDeleteShadow(){
       if (dataStorage.hasCollection(TEST_DELETE + SHADOW)) dataStorage.dropCollection(TEST_DELETE + SHADOW);
       if (dataStorage.hasCollection(TEST_DELETE + SHADOW + ".delete")) dataStorage.dropCollection(TEST_DELETE + SHADOW + ".delete");
       dataStorage.createCollection(TEST_DELETE + SHADOW);
       versionFacade.deleteVersionCollection(TEST_DELETE);
       Assert.assertTrue(dataStorage.hasCollection(TEST_DELETE + SHADOW + ".delete"));
    }
 */
   private boolean testForEquiv(DataDocument doc, DataDocument equivalent) {
      return doc.keySet().containsAll(equivalent.keySet()) && doc.size() == equivalent.size();
   }

   private DataDocument createTestDocument() {
      DataDocument dataDocument = new DataDocument();
      dataDocument.put("dog", "cat");
      dataDocument.put(VERSION_STRING, 1);
      return dataDocument;
   }

   private DataDocument createEmptyDocument() {
      DataDocument dataDocument = new DataDocument();
      dataDocument.put("dog", "cat");
      return dataDocument;
   }

   private String createCollection(String collectionName) {
      if (dataStorage.hasCollection(collectionName)) {
         dataStorage.dropCollection(collectionName);
      }
      String shadowCollectionName = versionFacade.buildShadowCollectionName(collectionName);
      if (dataStorage.hasCollection(shadowCollectionName)) {
         dataStorage.dropCollection(shadowCollectionName);
      }
      dataStorage.createCollection(collectionName);
      dataStorage.createCollection(shadowCollectionName);
      return shadowCollectionName;
   }
}