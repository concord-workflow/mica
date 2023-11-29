import { PreviewRequest, usePreview } from '../api/view.ts';
import { Alert, CircularProgress } from '@mui/material';

import React from 'react';

interface Props {
    source: () => PreviewRequest | undefined;
}

const PreviewView = ({ source }: Props) => {
    const { mutateAsync, data, isLoading, error } = usePreview();

    React.useEffect(() => {
        const request = source();
        if (!request) {
            return;
        }
        mutateAsync(request);
    }, [mutateAsync, source]);

    if (error) {
        return <Alert color="error">{error.message}</Alert>;
    }

    if (!data || isLoading) {
        return <CircularProgress />;
    }

    // TODO render as a table
    return <pre>{JSON.stringify(data, null, 2)}</pre>;
};

export default PreviewView;
