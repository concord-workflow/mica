import { doFetch, handleJsonResponse } from './common.ts';

export enum EntryType {
    FOLDER = 'FOLDER',
    FILE = 'FILE',
}

export interface Entry {
    type: EntryType;
    entityId?: string;
    name: string;
    entityKind?: string;
    deletedAt?: string;
}

export interface ListResponse {
    data: Array<Entry>;
}

export const ENTITY_SEARCH_LIMIT = 100;

export const list = (
    path: string,
    entityKind?: string,
    search?: string,
    deleted?: boolean,
): Promise<ListResponse> =>
    doFetch(
        `/api/mica/ui/entityList?path=${encodeURIComponent(path)}&entityKind=${
            entityKind ? encodeURIComponent(entityKind) : ''
        }&search=${search ? encodeURIComponent(search) : ''}&deleted=${deleted ? 'true' : ''}`,
    ).then(handleJsonResponse<ListResponse>);
