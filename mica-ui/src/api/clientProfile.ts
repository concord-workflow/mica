import { handleJsonResponse } from './common.ts';

export interface ClientProfile {
    id: string;
    name: string;
}

export interface ClientProfileList {
    data: Array<ClientProfile>;
}

export const listClientProfiles = (search?: string): Promise<ClientProfileList> =>
    fetch(`/api/mica/v1/clientProfile?search=${search}`).then(
        handleJsonResponse<ClientProfileList>,
    );
