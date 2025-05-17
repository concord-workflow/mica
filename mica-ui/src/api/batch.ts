import { doFetch, handleJsonResponse } from './common.ts';
import { DeletedEntityVersionAndName } from './entity.ts';
import { ApiError } from './error.ts';

import { UseMutationOptions, useMutation } from '@tanstack/react-query';

export enum BatchOperation {
    DELETE = 'DELETE',
}

export interface BatchOperationRequest {
    operation: BatchOperation;
    namePatterns: Array<string>;
}

export interface BatchOperationResult {
    deletedEntities?: Array<DeletedEntityVersionAndName>;
}

export const apply = (request: BatchOperationRequest): Promise<BatchOperationResult> =>
    doFetch('/api/mica/v1/batch', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<BatchOperationResult>);

export const useApplyBatchOperation = (
    options?: UseMutationOptions<BatchOperationResult, ApiError, BatchOperationRequest>,
) =>
    useMutation<BatchOperationResult, ApiError, BatchOperationRequest>({
        mutationFn: (request) => apply(request),
        ...options,
    });
