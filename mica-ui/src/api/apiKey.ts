import { doFetch, handleJsonResponse } from './common.ts';
import { ApiError } from './error.ts';

import { UseMutationOptions, useMutation } from '@tanstack/react-query';

export interface ApiKeyEntry {
    id: string;
    userId: string;
    name: string;
    expiredAt?: string;
}

export const listApiKeys = (): Promise<Array<ApiKeyEntry>> =>
    doFetch(`/api/v1/apikey`).then(handleJsonResponse<Array<ApiKeyEntry>>);

export interface CreateApiKeyRequest {
    name: string;
}

export interface CreateApiKeyResponse {
    id: string;
    key: string;
}

const createApiKey = (request: CreateApiKeyRequest): Promise<CreateApiKeyResponse> =>
    doFetch(`/api/v1/apikey`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<CreateApiKeyResponse>);

export const useCreateApiKey = (
    options?: UseMutationOptions<CreateApiKeyResponse, ApiError, CreateApiKeyRequest>,
) =>
    useMutation<CreateApiKeyResponse, ApiError, CreateApiKeyRequest>({
        mutationFn: (request) => createApiKey(request),
        ...options,
    });
