export interface Violation {
    id: string;
    message: string;
}

export interface ApiError {
    type: string;
    status: number;
    statusText: string;
    message: string;
    payload?: Array<Violation>;
}

const parseValidationError = async (resp: Response): Promise<ApiError> => {
    const payload: Array<Violation> = await resp.json();
    return {
        type: 'detailed-validation-error',
        message: 'Validation error',
        status: resp.status,
        statusText: resp.statusText,
        payload,
    };
};

const parseApiError = async (resp: Response): Promise<ApiError> => {
    const payload: ApiError = await resp.json();
    return {
        type: payload.type,
        message: payload.message,
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
