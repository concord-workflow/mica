import { JsonNode } from '../api/entity.ts';
import { PreviewRequest, usePreview } from '../api/view.ts';
import ReadableApiError from '../components/ReadableApiError.tsx';
import { Alert, Box, CircularProgress } from '@mui/material';
import { useDebounce } from '@uidotdev/usehooks';

import React from 'react';
import JsonView from 'react18-json-view';
import 'react18-json-view/src/style.css';

interface Props {
    request: PreviewRequest;
}

const PreviewView = ({ request }: Props) => {
    const [lastGoodData, setLastGoodData] = React.useState<JsonNode>(null);

    const { mutateAsync, isLoading, error } = usePreview({
        retry: false,
        onSuccess: (data) => {
            setLastGoodData(data.data);
        },
    });

    const debouncedRequest = useDebounce(request, 100);
    React.useEffect(() => {
        if (!debouncedRequest.view) {
            return;
        }

        mutateAsync(debouncedRequest);
    }, [mutateAsync, debouncedRequest]);

    const showLoadingIndicator = useDebounce(isLoading, 250);

    return (
        <>
            {error && (
                <Alert color="error" sx={{ position: 'relative' }}>
                    <ReadableApiError error={error} />
                </Alert>
            )}
            <Box>
                {showLoadingIndicator && (
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
                        <JsonView src={lastGoodData} />
                    </Box>
                )}
            </Box>
        </>
    );
};

export default PreviewView;
