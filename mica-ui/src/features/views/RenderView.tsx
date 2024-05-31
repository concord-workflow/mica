import { ApiError } from '../../api/error.ts';
import { RenderRequest, RenderResponse, render } from '../../api/view.ts';
import ReadableApiError from '../../components/ReadableApiError.tsx';
import DataView from './DataView.tsx';
import { Alert, Box, CircularProgress } from '@mui/material';

import { useQuery } from '@tanstack/react-query';

interface Props {
    request: RenderRequest;
}

const RenderView = ({ request }: Props) => {
    const { data, isLoading, error } = useQuery<RenderResponse, ApiError>({
        queryKey: ['render', request],
        queryFn: () => render(request),

        retry: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
    });

    return (
        <>
            {error && (
                <Alert color="error" sx={{ position: 'relative' }}>
                    <ReadableApiError error={error} />
                </Alert>
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
            {data && (
                <>
                    <DataView data={data.data} />
                </>
            )}
        </>
    );
};

export default RenderView;
