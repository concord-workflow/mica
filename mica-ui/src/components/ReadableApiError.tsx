import { ApiError } from '../api/error.ts';

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

    return <>{error.message}</>;
};

export default ReadableApiError;
