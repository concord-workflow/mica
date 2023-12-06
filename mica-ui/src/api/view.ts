import { doFetch, handleJsonResponse } from './common.ts';
import { Entity, PartialEntity } from './entity.ts';
import { ApiError } from './error.ts';

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

export const usePreview = (options?: UseMutationOptions<PartialEntity, ApiError, PreviewRequest>) =>
    useMutation<PartialEntity, ApiError, PreviewRequest>(preview, options);
