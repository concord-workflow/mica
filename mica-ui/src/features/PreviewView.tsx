import { PartialEntity } from '../api/entity.ts';
import { PreviewRequest, usePreview } from '../api/view.ts';
import ReadableApiError from '../components/ReadableApiError.tsx';
import ShowRenderedViewDetailsToggle from '../components/ShowRenderedViewDetailsToggle.tsx';
import CloseIcon from '@mui/icons-material/Close';
import { Alert, Box, CircularProgress, IconButton } from '@mui/material';
import { useDebounce } from '@uidotdev/usehooks';

import React from 'react';
import JsonView from 'react18-json-view';
import 'react18-json-view/src/style.css';

export interface PreviewRequestOrError {
    request?: PreviewRequest;
    error?: Error;
}

interface Props {
    version: number;
    requestFn: () => PreviewRequestOrError;
    onClose: () => void;
}

const PreviewView = ({ version, requestFn, onClose }: Props) => {
    const [lastGoodData, setLastGoodData] = React.useState<PartialEntity>();

    const {
        mutateAsync,
        isLoading,
        error: apiError,
    } = usePreview({
        retry: false,
        onSuccess: (data) => {
            setLastGoodData(data);
        },
    });

    const [requestError, setRequestError] = React.useState<Error>();

    const debouncedVersion = useDebounce(version, 500);
    React.useEffect(() => {
        const { request, error } = requestFn();
        setRequestError(error);
        if (request) {
            mutateAsync(request);
        }
    }, [mutateAsync, debouncedVersion, requestFn]);

    const debouncedIsLoading = useDebounce(isLoading, 250);

    const [showDetails, setShowDetails] = React.useState(false);

    return (
        <>
            {apiError && (
                <Alert color="error" sx={{ position: 'relative' }}>
                    <ReadableApiError error={apiError} />
                </Alert>
            )}
            {requestError && (
                <Alert color="error" sx={{ position: 'relative' }}>
                    {requestError.message}
                </Alert>
            )}
            <Box>
                <Box sx={{ position: 'absolute', right: 0 }}>
                    <ShowRenderedViewDetailsToggle
                        checked={showDetails}
                        onChange={(value) => setShowDetails(value)}
                    />
                    <IconButton onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                </Box>
                {debouncedIsLoading && (
                    <Box
                        display="flex"
                        alignItems="center"
                        justifyContent="center"
                        width="100%"
                        height="100%"
                        position="absolute"
                        bgcolor="#ffffffaa">
                        <CircularProgress color="secondary" />
                    </Box>
                )}
                {lastGoodData && (
                    <Box margin={1}>
                        <JsonView src={showDetails ? lastGoodData : lastGoodData.data} />
                    </Box>
                )}
            </Box>
        </>
    );
};

export default PreviewView;
