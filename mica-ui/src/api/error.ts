export interface ValidationError {
    id: string;
    message: string;
}

export interface ApiError {
    type: string;
    status: number;
    statusText: string;
    message: string;
    payload?: Array<ValidationError>;
}

export const parseApiError = async (resp: Response): Promise<ApiError> => {
    if (
        !resp.headers
            .get('content-type')
            ?.includes('application/vnd.concord-validation-errors-v1+json')
    ) {
        return {
            type: 'unknown',
            status: resp.status,
            statusText: resp.statusText,
            message: `${resp.status} ${resp.statusText}`,
        };
    }

    const payload: Array<ValidationError> = await resp.json();
    if (
        !Array.isArray(payload) ||
        payload.some((e) => !e.message || typeof e.message !== 'string')
    ) {
        throw new Error(`Invalid error response: ${JSON.stringify(payload)}`);
    }

    return {
        type: 'detailed-validation-error',
        payload,
        message: 'Validation error',
        status: resp.status,
        statusText: resp.statusText,
    };
};
