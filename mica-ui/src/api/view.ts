import { doFetch, handleJsonResponse } from './common.ts';
import { PartialEntity } from './entity.ts';
import { ApiError } from './error.ts';
import { JsonNode } from './schema.ts';

import { UseMutationOptions, useMutation } from '@tanstack/react-query';

export interface PreviewViewRequest {
    view: PartialEntity;
    parameters?: Record<string, string>;
}

const preview = (request: PreviewViewRequest): Promise<PartialEntity> =>
    doFetch(`/api/mica/v1/view/preview`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<PartialEntity>);

export const usePreview = (
    options?: UseMutationOptions<PartialEntity, ApiError, PreviewViewRequest>,
) => useMutation<PartialEntity, ApiError, PreviewViewRequest>({ mutationFn: preview, ...options });

export interface RenderViewRequest {
    viewId: string;
    parameters?: JsonNode;
}

export interface RenderViewResponse {
    data: JsonNode;
}

export const render = (request: RenderViewRequest): Promise<RenderViewResponse> =>
    doFetch(`/api/mica/v1/view/render`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<RenderViewResponse>);
