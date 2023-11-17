import { doFetch, handleErrors, handleJsonResponse } from './common.ts';

import { useMutation } from 'react-query';
import { UseMutationOptions } from 'react-query/types/react/types';

export interface ClientEndpoint {
    id: string;
    name: string;
    clientId: string;
    clientName: string;
    uri: string;
    lastKnownStatus: string;
    statusUpdatedAt: string;
}

export interface ClientEndpointList {
    data: Array<ClientEndpoint>;
}

export const listClientEndpoints = (search?: string): Promise<ClientEndpointList> =>
    doFetch(`/api/mica/v1/clientEndpoint?search=${search ?? ''}`).then(
        handleJsonResponse<ClientEndpointList>,
    );

const importFromClientData = (profileId: string): Promise<void> =>
    doFetch(`/api/mica/v1/clientEndpoint/importFromClientData`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ profileId }),
    }).then(handleErrors);

export const useImportFromClientData = (
    options?: UseMutationOptions<void, Error, { profileId: string }>,
) => {
    return useMutation<void, Error, { profileId: string }>(
        ({ profileId }) => importFromClientData(profileId),
        options,
    );
};
