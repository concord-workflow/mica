import { doFetch, handleJsonResponse } from './common.ts';
import { EntityVersionAndName } from './entity.ts';

export enum BatchOperation {
    DELETE = 'DELETE',
}

export interface BatchOperationRequest {
    operation: BatchOperation;
    namePatterns: Array<string>;
}

export interface BatchOperationResult {
    deletedEntities?: Array<EntityVersionAndName>;
}

export const apply = (request: BatchOperationRequest): Promise<BatchOperationResult> =>
    doFetch('/api/mica/v1/batch', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    }).then(handleJsonResponse<BatchOperationResult>);
