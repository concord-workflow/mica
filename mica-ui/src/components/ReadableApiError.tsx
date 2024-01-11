import { ApiError } from '../api/error.ts';
import { ErrorKind, ErrorPayload } from '../api/validation.ts';

interface Props {
    error: ApiError | null | undefined;
}

const ReadableApiError = ({ error }: Props) => {
    if (!error) {
        return null;
    }

    if (error.type === 'concord-validation-error') {
        return (
            <>
                {error.message}
                {error.violations &&
                    error.violations.map((e) => (
                        <div key={e.id}>
                            <i>{e.id}</i> property is invalid: {e.message}
                        </div>
                    ))}
            </>
        );
    }

    if (error.type === 'detailed-validation-error' && error.payload) {
        const { errors } = error.payload as unknown as ErrorPayload;
        return (
            <>
                {error.message}
                {Object.keys(errors).map((key) => {
                    const prop = errors[key];
                    if (prop.kind === ErrorKind.MISSING_PROPERTY) {
                        return <div>${key} property is required</div>;
                    }
                    // TODO other error kinds
                    return (
                        <div>
                            <i>{key}</i> property is invalid: {prop.kind}
                        </div>
                    );
                })}
            </>
        );
    }

    return <>{error.message}</>;
};

export default ReadableApiError;
