import { RenderRequest, useRender } from '../../api/view.ts';
import ReadableApiError from '../../components/ReadableApiError.tsx';
import DataView from './DataView.tsx';
import ShowRenderedViewDetailsToggle from './ShowRenderedViewDetailsToggle.tsx';
import { Alert, Box, CircularProgress, styled } from '@mui/material';

import React from 'react';

interface Props {
    request: RenderRequest;
}

const FloatingBox = styled(Box)(() => ({ float: 'right' }));

const RenderView = ({ request }: Props) => {
    const { mutateAsync, data, isLoading, error } = useRender({
        retry: false,
    });

    React.useEffect(() => {
        mutateAsync(request);
    }, [mutateAsync, request]);

    const [showDetails, setShowDetails] = React.useState(false);
    const handleDetailsToggle = React.useCallback((value: boolean) => {
        setShowDetails(value);
    }, []);

    return (
        <>
            {error && (
                <Alert color="error" sx={{ position: 'relative' }}>
                    <ReadableApiError error={error} />
                </Alert>
            )}
            <Box>
                {data && (
                    <FloatingBox>
                        <ShowRenderedViewDetailsToggle
                            checked={showDetails}
                            onChange={handleDetailsToggle}
                        />
                    </FloatingBox>
                )}
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
                {data && <DataView data={showDetails ? data : data.data} />}
            </Box>
        </>
    );
};

export default RenderView;
