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
}

export interface ListResponse {
    data: Array<Entry>;
}

export const list = (path: string, entityKind?: string): Promise<ListResponse> =>
    doFetch(
        `/api/mica/ui/entityList?path=${encodeURIComponent(path)}&entityKind=${
            entityKind ? encodeURIComponent(entityKind) : ''
        }`,
    ).then(handleJsonResponse<ListResponse>);
