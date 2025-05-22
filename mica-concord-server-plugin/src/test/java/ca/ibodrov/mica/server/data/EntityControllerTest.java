package ca.ibodrov.mica.server.data;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.tables.MicaEntities.MICA_ENTITIES;
import static ca.ibodrov.mica.server.data.UserEntryUtils.user;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

public class EntityControllerTest extends AbstractDatabaseTest {

    private static final UserPrincipal session = new UserPrincipal("test", user("test"));
    private static YamlMapper yamlMapper;
    private static EntityController controller;

    @BeforeAll
    public static void setUp() {
        yamlMapper = new YamlMapper(objectMapper);
        var entityKindStore = new EntityKindStore(entityStore);
        var historyController = new EntityHistoryController(dsl());
        controller = new EntityController(dsl(), entityStore, entityKindStore, historyController, objectMapper);

        // insert the built-in entity kinds
        new InitialDataLoader(dsl(), entityStore, objectMapper).load();
    }

    private static void createOrUpdate(PartialEntity entity) {
        dsl().transaction(tx -> controller.createOrUpdate(tx.dsl(), EntityControllerTest.session, entity, null, false));
    }

    private static EntityVersion createOrUpdate(PartialEntity entity,
                                                String doc,
                                                boolean overwrite) {
        return dsl().transactionResult(
                tx -> controller.createOrUpdate(tx.dsl(), EntityControllerTest.session, entity, doc, overwrite));
    }

    private static EntityVersion upsert(PartialEntity entity) {
        return dsl().transactionResult(tx -> entityStore.upsert(tx.dsl(), entity, null).orElseThrow());
    }

    @Test
    public void testUploadUnknownEntityKind() {
        var yaml = """
                kind: someRandomKind
                name: foobar
                data: |
                  some text
                """;

        var error = assertThrows(ApiException.class, () -> createOrUpdate(parseYaml(yaml)));
        assertEquals(BAD_REQUEST, error.getStatus());
    }

    @Test
    public void testUploadBuiltInEntityKinds() {
        createOrUpdate(parseYaml("""
                kind: /mica/record/v1
                name: %s
                data: |
                  some text
                """.formatted(randomEntityName())));

        createOrUpdate(parseYaml("""
                kind: /mica/kind/v1
                name: %s
                schema:
                  type: object
                  properties:
                    foo:
                      type: string
                """.formatted(randomEntityName())));

        createOrUpdate(parseYaml("""
                kind: /mica/view/v1
                name: %s
                selector:
                  entityKind: /mica/record/v1
                data:
                  jsonPath: $.data
                """.formatted(randomEntityName())));
    }

    @Test
    public void testUploadInvalidEntity() {
        // missing property
        var entity1 = parseYaml("""
                kind: /mica/record/v1
                name: %s
                randomProp: "foo"
                """.formatted(randomEntityName()));
        var error1 = assertThrows(ValidationErrorsException.class, () -> createOrUpdate(entity1));
        assertEquals(1, error1.getValidationErrors().size());
    }

    @Test
    public void testDocBeingUpdatedAfterUpsert() {
        var doc = """
                kind: /mica/record/v1
                name: "%s"
                data: foo! # inline comment
                # some other comment
                """.formatted(randomEntityName());

        var entity = parseYaml(doc);
        var initialVersion = createOrUpdate(entity, doc, false);
        var createdAt = initialVersion.updatedAt();
        var updatedDoc = entityStore.getEntityDoc(initialVersion).orElseThrow();

        var expected = """
                id: "%s"
                createdAt: "%s"
                updatedAt: "%s"
                kind: "/mica/record/v1"
                name: "%s"
                data: foo! # inline comment
                # some other comment
                """.formatted(initialVersion.id().toExternalForm(),
                objectMapper.convertValue(createdAt, String.class),
                objectMapper.convertValue(initialVersion.updatedAt(), String.class),
                entity.name());

        assertEquals(expected, updatedDoc);

        entity = parseYaml(updatedDoc);
        var updatedVersion = createOrUpdate(entity, updatedDoc, false);
        updatedDoc = entityStore.getEntityDoc(initialVersion).orElseThrow();

        expected = """
                id: "%s"
                createdAt: "%s"
                updatedAt: "%s"
                kind: "/mica/record/v1"
                name: "%s"
                data: foo! # inline comment
                # some other comment
                """.formatted(updatedVersion.id().toExternalForm(),
                objectMapper.convertValue(createdAt, String.class),
                objectMapper.convertValue(updatedVersion.updatedAt(), String.class),
                entity.name());

        assertEquals(expected, updatedDoc);
    }

    @Test
    public void usersCanOverwriteConflicts() {
        // create the initial version

        var doc = """
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(randomEntityName());

        var entity = parseYaml(doc);
        var initialVersion = createOrUpdate(entity, doc, false);

        // fetch the created document

        var createdDoc = entityStore.getEntityDoc(initialVersion).orElseThrow();

        // modify and update the document as if it was done by a user
        var updatedDoc = createdDoc + "\n # updated by user1";
        var updatedVersion = createOrUpdate(parseYaml(updatedDoc), updatedDoc,
                false);
        assertEquals(initialVersion.id(), updatedVersion.id());
        assertNotEquals(initialVersion.updatedAt(), updatedVersion.updatedAt());

        // modify and try updating the same original document as if it was done by
        // another user
        var updatedDocAlternative = createdDoc + "\n # updated by user2";
        var error = assertThrows(ApiException.class,
                () -> createOrUpdate(parseYaml(updatedDocAlternative),
                        updatedDocAlternative,
                        false));
        assertEquals(CONFLICT, error.getStatus());

        // overwrite the document
        var overwrittenVersion = createOrUpdate(parseYaml(updatedDocAlternative),
                updatedDocAlternative, true);
        assertEquals(initialVersion.id(), overwrittenVersion.id());
        assertNotEquals(initialVersion.updatedAt(), overwrittenVersion.updatedAt());
    }

    @Test
    public void avoidFileAndFolderNameClashes() {
        var namePrefix = "/test_" + UUID.randomUUID();

        var entityFooBar = parseYaml("""
                kind: /mica/record/v1
                name: %s/foo/bar
                data: |
                  some text
                """.formatted(namePrefix));

        createOrUpdate(entityFooBar);

        var entityFoo = parseYaml("""
                kind: /mica/record/v1
                name: %s/foo
                data: |
                  some text
                """.formatted(namePrefix));

        var error = assertThrows(StoreException.class, () -> createOrUpdate(entityFoo));
        assertTrue(error.getMessage().contains("is a folder"));
    }

    @Test
    public void submittingTheSameDocTwiceShouldReturnTheSameVersion() {
        // create the initial version
        var namePrefix = "/test_" + UUID.randomUUID();
        var docFoo = """
                kind: /mica/record/v1
                name: %s/foo
                data: |
                  hello!
                """.formatted(namePrefix);
        var entityFoo = parseYaml(docFoo);
        var initialVersion = createOrUpdate(entityFoo, docFoo, false);

        // grab the saved doc and try saving it again, there should be no changes
        var updatedDoc = entityStore.getEntityDoc(initialVersion).orElseThrow();
        entityFoo = parseYaml(updatedDoc);
        var updatedVersion = createOrUpdate(entityFoo, updatedDoc, false);
        assertEquals(initialVersion, updatedVersion);

        // modify the saved doc and try saving it, there should be a new version
        var updatedDoc2 = updatedDoc.replace("hello!", "bye!");
        entityFoo = parseYaml(updatedDoc2);
        assertEquals("bye!\n", entityFoo.data().get("data").asText());
        var updatedVersion2 = createOrUpdate(entityFoo, updatedDoc2, false);
        assertNotEquals(initialVersion, updatedVersion2);
        assertEquals(initialVersion.id(), updatedVersion2.id());
    }

    @Test
    public void nameAndKindAreReplacedInDoc() {
        upsert(parseYaml("""
                name: /replacement/kind
                kind: /mica/kind/v1
                schema:
                  properties:
                    data:
                      type: string
                """));

        var doc = """
                kind: /mica/record/v1
                name: /original/name
                data: |
                  hello!
                """;
        var entity = parseYaml(doc)
                .withName("/replacement/name")
                .withKind("/replacement/kind");
        createOrUpdate(entity, doc, false);

        var updatedEntity = entityStore.getByName("/replacement/name")
                .orElseThrow();
        assertEquals("/replacement/name", updatedEntity.name());
        assertEquals("/replacement/kind", updatedEntity.kind());

        var updatedDoc = entityStore.getEntityDoc(updatedEntity.version())
                .orElseThrow();
        assertTrue(updatedDoc.contains("name: \"/replacement/name\""));
        assertTrue(updatedDoc.contains("kind: \"/replacement/kind\""));
    }

    @Test
    public void multipleEntitiesWithTheSameNameCanBeMarkedAsDeleted() {
        // create the initial "foo"
        String entityName = "/test_delete_" + UUID.randomUUID() + "/foo";
        var initialDoc = """
                kind: /mica/record/v1
                name: %s
                data: |
                  initial entity
                """.formatted(entityName);
        var initialVersion = createOrUpdate(parseYaml(initialDoc), initialDoc, false);

        var initialFoo = entityStore.getByName(entityName).orElseThrow();
        assertEquals(initialVersion.id(), initialFoo.version().id());

        // delete the initial "foo"

        controller.deleteById(session, initialVersion.id());
        assertTrue(entityStore.getByName(entityName).isEmpty());

        // create the replacement "foo"

        var replacementDoc = """
                kind: /mica/record/v1
                name: %s
                data: |
                  replacement entity
                """.formatted(entityName);
        var replacementVersion = createOrUpdate(parseYaml(replacementDoc), replacementDoc, false);

        var replacementFoo = entityStore.getByName(entityName).orElseThrow();
        assertEquals(replacementVersion.id(), replacementFoo.version().id());
        assertNotEquals(initialVersion.id(), replacementVersion.id());

        // delete the replacement "foo"

        controller.deleteById(session, replacementVersion.id());
        assertTrue(entityStore.getByName(entityName).isEmpty());

        // check that both entities exists and marked as deleted

        initialFoo = entityStore.getById(initialFoo.id()).orElseThrow();
        dsl().select(MICA_ENTITIES.DELETED_AT).from(MICA_ENTITIES).where(MICA_ENTITIES.ID.eq(initialFoo.id().id()))
                .fetchOptional(MICA_ENTITIES.DELETED_AT)
                .orElseThrow();

        replacementFoo = entityStore.getById(replacementFoo.id()).orElseThrow();
        dsl().select(MICA_ENTITIES.DELETED_AT).from(MICA_ENTITIES).where(MICA_ENTITIES.ID.eq(replacementFoo.id().id()))
                .fetchOptional(MICA_ENTITIES.DELETED_AT)
                .orElseThrow();

        assertEquals(initialFoo.name(), replacementFoo.name());
    }

    @Test
    public void putWithReplaceDoesNotDeleteEntities() {
        // create the initial "foo"
        String entityName = "/test_put_%s/foo".formatted(UUID.randomUUID());
        var initialDoc = """
                kind: /mica/record/v1
                name: %s
                data: |
                  initial entity
                """.formatted(entityName);

        var initialVersion = createOrUpdate(parseYaml(initialDoc), initialDoc, false);
        var updatedDoc = entityStore.getEntityDoc(initialVersion).orElseThrow();

        updatedDoc = updatedDoc.replace("initial", "updated");
        var replacedVersion = controller.put(session, parseYaml(updatedDoc), updatedDoc, false, true);
        assertEquals(initialVersion.id(), replacedVersion.id());

        var replacedEntity = entityStore.getById(replacedVersion.id()).orElseThrow();
        assertTrue(replacedEntity.deletedAt().isEmpty());
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String randomEntityName() {
        return "test_" + UUID.randomUUID();
    }
}
