import { doFetch, handleJsonResponse, handleTextResponse } from './common.ts';
import { ApiError } from './error.ts';
import { JsonNode } from './schema.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export const MICA_KIND_KIND = '/mica/kind/v1';
export const MICA_RECORD_KIND = '/mica/record/v1';
export const MICA_VIEW_KIND = '/mica/view/v1';

const GENERIC_TEMPLATE = `# new entity
name: %%NAME%%
kind: %%KIND%%
myProperty: true
`;

const MICA_RECORD_TEMPLATE = `# new entity
name: %%NAME%%
kind: ${MICA_RECORD_KIND}
data:
  myProperty: true
`;

const MICA_KIND_TEMPLATE = `# new kind
name: %%NAME%%
kind: ${MICA_KIND_KIND}
schema:
  properties:
     myProperty:
       type: boolean
`;

const MICA_VIEW_TEMPLATE = `# new view
name: %%NAME%%
kind: ${MICA_VIEW_KIND}
selector:
  entityKind: ${MICA_RECORD_KIND}
data:
  jsonPath: $.myProperty
`;

const lookupTemplateByKind = (kind: string): string => {
    switch (kind) {
        case MICA_RECORD_KIND:
            return MICA_RECORD_TEMPLATE;
        case MICA_KIND_KIND:
            return MICA_KIND_TEMPLATE;
        case MICA_VIEW_KIND:
            return MICA_VIEW_TEMPLATE;
        default:
            return GENERIC_TEMPLATE;
    }
};

export const kindToTemplate = (name: string, kind: string): string => {
    const template = lookupTemplateByKind(kind);
    return template.replace('%%KIND%%', kind).replace('%%NAME%%', name);
};

export interface EntityEntry {
    id: string;
    name: string;
    kind: string;
    createdAt: string;
    updatedAt: string;
}

export interface EntityList {
    data: Array<EntityEntry>;
}

export interface EntityVersion {
    id: string;
    updatedAt: string;
}

export interface Entity {
    id: string;
    name: string;
    kind: string;
    createdAt: string;
    updatedAt: string;

    [key: string]: JsonNode;
}

export interface PartialEntity {
    name: string;
    kind: string;

    [key: string]: JsonNode;
}

export enum OrderBy {
    NAME = 'NAME',
}

export const STANDARD_ENTITY_PROPERTIES = ['id', 'name', 'kind', 'createdAt', 'updatedAt'];

export const getEntity = (id: string): Promise<Entity> =>
    doFetch(`/api/mica/v1/entity/${id}`).then(handleJsonResponse<Entity>);

export const getEntityDoc = (id: string): Promise<string> =>
    doFetch(`/api/mica/v1/entity/${id}/doc`).then(handleTextResponse);

const qp = (key: string, value: string | number | undefined): string =>
    value ? `${key}=${encodeURIComponent(value)}` : '';

export interface ListEntitiesRequest {
    entityNameStartsWith?: string;
    entityName?: string;
    entityKind?: string;
    orderBy?: OrderBy;
    limit?: number;
}

export const listEntities = (request: ListEntitiesRequest): Promise<EntityList> => {
    const queryParams = Object.keys(request)
        .map((key) => qp(key, request[key as keyof ListEntitiesRequest]))
        .join('&');
    return doFetch(`/api/mica/v1/entity?${queryParams}`).then(handleJsonResponse<EntityList>);
};

const deleteById = (entityId: string): Promise<EntityVersion> =>
    doFetch(`/api/mica/v1/entity/${entityId}`, {
        method: 'DELETE',
    }).then(handleJsonResponse<EntityVersion>);

export const useDeleteById = (
    options?: UseMutationOptions<EntityVersion, ApiError, { entityId: string }>,
) =>
    useMutation<EntityVersion, ApiError, { entityId: string }>(
        ({ entityId }) => deleteById(entityId),
        options,
    );
