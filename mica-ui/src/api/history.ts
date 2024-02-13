import { doFetch, handleJsonResponse, handleTextResponse } from './common.ts';

export interface EntityHistoryEntry {
    entityId: string;
    updatedAt: string;
    operationType: 'UPDATE' | 'DELETE';
    author: string;
}

export interface EntityHistory {
    data: Array<EntityHistoryEntry>;
}

export const listHistory = (entityId: string, limit: number): Promise<EntityHistory> =>
    doFetch(`/api/mica/v1/history/${entityId}?limit=${limit}`).then(
        handleJsonResponse<EntityHistory>,
    );

export const getHistoryDoc = (entityId: string, updatedAt: string): Promise<string> =>
    doFetch(`/api/mica/v1/history/${entityId}/${updatedAt}/doc`).then(handleTextResponse);
