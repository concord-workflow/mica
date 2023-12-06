import { JsonNode } from './entity.ts';

export interface ApiError {
    type: string;
    status: number;
    statusText: string;
    message: string;
    payload?: JsonNode;
}

export const parseApiError = async (resp: Response): Promise<ApiError> => {
    if (!resp.headers.get('content-type')?.includes('application/json')) {
        return {
            type: 'unknown',
            status: resp.status,
            statusText: resp.statusText,
            message: `${resp.status} ${resp.statusText}`,
        };
    }

    const error = await resp.json();
    if (!error.message) {
        throw new Error(`Invalid error response: ${JSON.stringify(error)}`);
    }
    return {
        ...error,
        status: resp.status,
        statusText: resp.statusText,
    };
};
