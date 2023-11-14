import { handleJsonResponse } from './common.ts';

export interface ClientData {
    properties: object;
}

export const getLatestData = (externalId: string): Promise<ClientData> =>
    fetch(`/api/mica/v1/clientData/latest?externalId=${externalId}`).then(
        handleJsonResponse<ClientData>,
    );
