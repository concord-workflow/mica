import { QueryClient, useMutation } from 'react-query';

export interface ImportResponse {
    documentId: string;
}

const importClientData = (file: File): Promise<ImportResponse> =>
    fetch('/api/mica/v1/clientData/import', {
        method: 'POST',
        body: file,
    }).then((resp) => resp.json());

export const useImportClientData = (client: QueryClient) =>
    useMutation<ImportResponse, Error, { file: File }>(({ file }) => importClientData(file), {
        onSuccess: () => {
            client.invalidateQueries('clientList');
        },
    });

export interface ClientData {
    properties: object;
}

export const getLatestData = (externalId: string): Promise<ClientData> =>
    fetch(`/api/mica/v1/clientData/latest?externalId=${externalId}`).then((resp) => resp.json());
