import { doFetch, handleJsonResponse } from './common.ts';
import { EntityVersion } from './entity.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

const putPartialYaml = (file: File, entityName?: string): Promise<EntityVersion> =>
    doFetch(
        `/api/mica/v1/upload/partialYaml?entityName=${
            entityName ? encodeURIComponent(entityName) : ''
        }`,
        {
            method: 'PUT',
            headers: {
                'Content-Type': file.type,
            },
            body: file,
        },
    ).then(handleJsonResponse<EntityVersion>);

const putYamlString = (body: string): Promise<EntityVersion> =>
    doFetch('/api/mica/v1/upload/yaml', {
        method: 'PUT',
        headers: {
            'Content-Type': 'text/yaml',
        },
        body,
    }).then(handleJsonResponse<EntityVersion>);

interface PutYamlFileRequest {
    file: File;
    entityName?: string;
}

export const usePutPartialYaml = (
    options?: UseMutationOptions<EntityVersion, Error, PutYamlFileRequest>,
) =>
    useMutation<EntityVersion, Error, PutYamlFileRequest>(
        ({ file, entityName }) => putPartialYaml(file, entityName),
        options,
    );

export const usePutYamlString = (
    options?: UseMutationOptions<EntityVersion, Error, { body: string }>,
) =>
    useMutation<EntityVersion, Error, { body: string }>(({ body }) => putYamlString(body), options);
