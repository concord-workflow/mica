import { RenderRequest, useRender } from '../api/view.ts';
import ReadableApiError from '../components/ReadableApiError.tsx';
import ShowRenderedViewDetailsToggle from '../components/ShowRenderedViewDetailsToggle.tsx';
import { Alert, Box, CircularProgress } from '@mui/material';

import React from 'react';
import JsonView from 'react18-json-view';
import 'react18-json-view/src/style.css';

interface Props {
    request: RenderRequest;
}

const RenderView = ({ request }: Props) => {
    const { mutateAsync, data, isLoading, error } = useRender({
        retry: false,
    });

    React.useEffect(() => {
        mutateAsync(request);
    }, [mutateAsync, request]);

    const [showDetails, setShowDetails] = React.useState(false);

    return (
        <>
            {error && (
                <Alert color="error" sx={{ position: 'relative' }}>
                    <ReadableApiError error={error} />
                </Alert>
            )}
            <Box>
                <Box sx={{ float: 'right' }}>
                    <ShowRenderedViewDetailsToggle
                        checked={showDetails}
                        onChange={(value) => setShowDetails(value)}
                    />
                </Box>
                {isLoading && (
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
                {data && (
                    <Box margin={1}>
                        <JsonView src={showDetails ? data : data.data} />
                    </Box>
                )}
            </Box>
        </>
    );
};

export default RenderView;
