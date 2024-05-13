import { doFetch, handleJsonResponse } from './common.ts';
import { PartialEntity } from './entity.ts';
import { ApiError } from './error.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export interface PreviewRequest {
    view: PartialEntity;
    parameters?: Record<string, string>;
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

export interface RenderRequest {
    viewId: string;
}

export const render = (request: RenderRequest): Promise<PartialEntity> =>
    doFetch(`/api/mica/v1/view/render`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<PartialEntity>);
