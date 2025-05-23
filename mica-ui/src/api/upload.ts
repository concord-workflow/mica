import { doFetch, handleJsonResponse } from './common.ts';
import { EntityVersion } from './entity.ts';
import { ApiError } from './error.ts';

import { UseMutationOptions, useMutation } from '@tanstack/react-query';

const putPartialYaml = (
    file: File,
    entityName?: string,
    entityKind?: string,
): Promise<EntityVersion> =>
    doFetch(
        `/api/mica/v1/upload/partialYaml?entityName=${
            entityName ? encodeURIComponent(entityName) : ''
        }&entityKind=${entityKind ? encodeURIComponent(entityKind) : ''}`,
        {
            method: 'PUT',
            headers: {
                'Content-Type': file.type,
            },
            body: file,
        },
    ).then(handleJsonResponse<EntityVersion>);

const putYamlString = (body: string, overwrite: boolean): Promise<EntityVersion> =>
    doFetch('/api/mica/v1/upload/yaml?overwrite=' + overwrite, {
        method: 'PUT',
        headers: {
            'Content-Type': 'text/yaml',
        },
        body,
    }).then(handleJsonResponse<EntityVersion>);

interface PutYamlFileRequest {
    file: File;
    entityName?: string;
    entityKind?: string;
}

export const usePutPartialYaml = (
    options?: UseMutationOptions<EntityVersion, ApiError, PutYamlFileRequest>,
) =>
    useMutation<EntityVersion, ApiError, PutYamlFileRequest>({
        mutationFn: ({ file, entityName, entityKind }) =>
            putPartialYaml(file, entityName, entityKind),
        ...options,
    });

interface PutYamlStringRequest {
    body: string;
    overwrite: boolean;
}

export const usePutYamlString = (
    options?: UseMutationOptions<EntityVersion, ApiError, PutYamlStringRequest>,
) =>
    useMutation<EntityVersion, ApiError, PutYamlStringRequest>({
        mutationFn: ({ body, overwrite }) => putYamlString(body, overwrite),
        ...options,
    });
