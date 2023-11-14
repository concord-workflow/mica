import { handleJsonResponse } from './common.ts';

export interface Client {
    id: string;
    name: string;
    properties: {
        status?: string;
    };
}

export interface ClientList {
    data: Array<Client>;
}

export const listClients = (search?: string, props?: Array<string>): Promise<ClientList> =>
    fetch(
        `/api/mica/v1/client?search=${search}${props?.map((prop) => `&props=${prop}`).join()}`,
    ).then(handleJsonResponse<ClientList>);
