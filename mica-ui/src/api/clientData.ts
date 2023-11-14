import { doFetch, handleJsonResponse } from './common.ts';

export interface ClientData {
    properties: object;
}

export const getLatestData = (externalId: string): Promise<ClientData> =>
    doFetch(`/api/mica/v1/clientData/latest?externalId=${externalId}`).then(
        handleJsonResponse<ClientData>,
    );
