import { doFetch, handleJsonResponse, handleTextResponse } from './common.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export const MICA_KIND_KIND = '/mica/kind/v1';
export const MICA_RECORD_KIND = '/mica/record/v1';
export const MICA_VIEW_KIND = '/mica/view/v1';

const GENERIC_TEMPLATE = `# new entity
name: %%NAME%%
kind: %%KIND%%
data:
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

export type JsonNode =
    | { [key: string]: JsonNode }
    | Array<JsonNode>
    | string
    | number
    | boolean
    | null;

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

export const getEntityAsYamlString = (id: string): Promise<string> =>
    doFetch(`/api/mica/v1/entity/${id}/yaml`).then(handleTextResponse);

const qp = (key: string, value: string | number | undefined): string =>
    value ? `${key}=${encodeURIComponent(value)}` : '';

export const listEntities = (
    search?: string,
    entityName?: string,
    entityKind?: string,
    orderBy?: OrderBy,
    limit?: number,
): Promise<EntityList> => {
    const queryParams = [
        qp('search', search),
        qp('entityName', entityName),
        qp('entityKind', entityKind),
        qp('orderBy', orderBy),
        qp('limit', limit),
    ].join('&');
    return doFetch(`/api/mica/v1/entity?${queryParams}`).then(handleJsonResponse<EntityList>);
};

const deleteById = (entityId: string): Promise<EntityVersion> =>
    doFetch(`/api/mica/v1/entity/${entityId}`, {
        method: 'DELETE',
    }).then(handleJsonResponse<EntityVersion>);

export const useDeleteById = (
    options?: UseMutationOptions<EntityVersion, Error, { entityId: string }>,
) =>
    useMutation<EntityVersion, Error, { entityId: string }>(
        ({ entityId }) => deleteById(entityId),
        options,
    );
