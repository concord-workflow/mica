export enum ErrorKind {
    MISSING_PROPERTY = 'MISSING_PROPERTY',
    INVALID_VALUE = 'INVALID_VALUE',
    INVALID_TYPE = 'INVALID_TYPE',
    INVALID_SCHEMA = 'INVALID_SCHEMA',
    UNEXPECTED_VALUE = 'UNEXPECTED_VALUE',
}

export interface ValidationError {
    kind: ErrorKind;
    metadata: object;
}

export interface ErrorPayload {
    errors: {
        [key: string]: ValidationError;
    };
}
