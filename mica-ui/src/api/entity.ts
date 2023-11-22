import { doFetch, handleJsonResponse, handleTextResponse } from './common.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

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

export interface EntityWithData extends EntityEntry {
    data: object;
}

export const getEntity = (id: string): Promise<EntityWithData> =>
    doFetch(`/api/mica/v1/entity/${id}`).then(handleJsonResponse<EntityWithData>);

export const getEntityAsYamlString = (id: string): Promise<string> =>
    doFetch(`/api/mica/v1/entity/${id}/yaml`).then(handleTextResponse);

export const listEntities = (search?: string): Promise<EntityList> =>
    doFetch(`/api/mica/v1/entity?search=${search ?? ''}`).then(handleJsonResponse<EntityList>);

const putYaml = (file: File): Promise<EntityVersion> =>
    doFetch('/api/mica/v1/entity', {
        method: 'PUT',
        headers: {
            'Content-Type': file.type,
        },
        body: file,
    }).then(handleJsonResponse<EntityVersion>);

const putYamlString = (body: string): Promise<EntityVersion> =>
    doFetch('/api/mica/v1/entity', {
        method: 'PUT',
        headers: {
            'Content-Type': 'text/yaml',
        },
        body,
    }).then(handleJsonResponse<EntityVersion>);

export const usePutYaml = (options?: UseMutationOptions<EntityVersion, Error, { file: File }>) =>
    useMutation<EntityVersion, Error, { file: File }>(({ file }) => putYaml(file), options);

export const usePutYamlString = (
    options?: UseMutationOptions<EntityVersion, Error, { body: string }>,
) =>
    useMutation<EntityVersion, Error, { body: string }>(({ body }) => putYamlString(body), options);
