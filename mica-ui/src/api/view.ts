import { doFetch, handleJsonResponse } from './common.ts';
import { Entity, PartialEntity } from './entity.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export const render = (viewId: string, limit?: number): Promise<Entity> =>
    doFetch(`/api/mica/v1/view/render/${viewId}?limit=${encodeURIComponent(limit ?? '')}`).then(
        handleJsonResponse<Entity>,
    );

export interface PreviewRequest {
    view: PartialEntity;
    limit: number;
}

const preview = (request: PreviewRequest): Promise<PartialEntity> =>
    doFetch(`/api/mica/v1/view/preview`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<PartialEntity>);

export const usePreview = (options?: UseMutationOptions<PartialEntity, Error, PreviewRequest>) =>
    useMutation<PartialEntity, Error, PreviewRequest>(preview, options);
