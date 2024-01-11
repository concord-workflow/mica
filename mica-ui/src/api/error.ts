import { JsonNode } from './schema.ts';

export interface Violation {
    id: string;
    message: string;
}

export interface ApiError {
    type: string;
    status: number;
    statusText: string;
    message: string;
    violations?: Array<Violation>;
    payload?: JsonNode;
}

const parseValidationError = async (resp: Response): Promise<ApiError> => {
    const violations: Array<Violation> = await resp.json();
    return {
        type: 'concord-validation-error',
        message: 'Validation error',
        status: resp.status,
        statusText: resp.statusText,
        violations,
    };
};

const parseApiError = async (resp: Response): Promise<ApiError> => {
    const { type, message, payload } = await resp.json();
    return {
        type,
        message,
        payload,
        status: resp.status,
        statusText: resp.statusText,
    };
};

export const parseError = async (resp: Response): Promise<ApiError> => {
    const contentType = resp.headers.get('content-type') ?? '';
    if (contentType.includes('application/vnd.concord-validation-errors-v1+json')) {
        return await parseValidationError(resp);
    } else if (contentType.includes('application/json')) {
        return await parseApiError(resp);
    }
    return {
        type: 'unknown-error',
        message: 'Unknown error',
        status: resp.status,
        statusText: resp.statusText,
    };
};
