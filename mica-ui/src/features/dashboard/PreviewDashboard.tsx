import {
    DashboardRenderResponse,
    PreviewDashboardRequest,
    usePreview,
} from '../../api/dashboard.ts';
import ReadableApiError from '../../components/ReadableApiError.tsx';
import RenderDashboard from './RenderDashboard.tsx';
import CloseIcon from '@mui/icons-material/Close';
import { Alert, Box, CircularProgress, IconButton } from '@mui/material';
import { useDebounce } from '@uidotdev/usehooks';
import { parse as parseYaml } from 'yaml';

import React, { PropsWithChildren } from 'react';

interface PreviewRequestOrError {
    request?: PreviewDashboardRequest;
    error?: Error;
}

const Overlay = ({ children }: PropsWithChildren) => (
    <Box
        display="flex"
        alignItems="center"
        justifyContent="center"
        width="100%"
        height="100%"
        position="absolute"
        bgcolor="#ffffffaa">
        {children}
    </Box>
);

const parseData = (data: string): PreviewRequestOrError => {
    if (data.length === 0) {
        return {};
    }
    try {
        const dashboard = parseYaml(data);

        if (!dashboard.view) {
            return { error: new Error('Dashboard "view" is required') };
        }

        if (!dashboard.layout) {
            return { error: new Error('Dashboard "layout" is required') };
        }

        delete dashboard.id;

        return {
            request: {
                dashboard,
            },
        };
    } catch (e) {
        return { error: new Error((e as Error).message) };
    }
};

interface Props {
    data: string;
    onClose: () => void;
}

const PreviewDashboard = ({ data, onClose }: Props) => {
    const [lastGoodData, setLastGoodData] = React.useState<DashboardRenderResponse>();

    const {
        mutateAsync,
        isPending,
        error: apiError,
    } = usePreview({
        retry: false,
        onSuccess: (data) => {
            setLastGoodData(data);
        },
    });

    const debouncedData = useDebounce(data, 500);
    const { request, error: requestError } = React.useMemo(
        () => parseData(debouncedData),
        [debouncedData],
    );
    React.useEffect(() => {
        if (!request) {
            return;
        }
        mutateAsync({ ...request });
    }, [mutateAsync, request]);

    const debouncedIsPending = useDebounce(isPending, 250);

    return (
        <Box height="100%" sx={{ m: 1 }}>
            {debouncedIsPending && (
                <Overlay>
                    <CircularProgress color="secondary" />
                </Overlay>
            )}
            {requestError && <Overlay />}
            <Box position="fixed" sx={{ mt: 1 }} right={(theme) => theme.spacing(2)} zIndex={100}>
                <IconButton onClick={onClose}>
                    <CloseIcon />
                </IconButton>
            </Box>
            {apiError && (
                <Alert color="error" sx={{ m: 1 }}>
                    <ReadableApiError error={apiError} />
                </Alert>
            )}
            {lastGoodData && (
                <RenderDashboard dashboard={lastGoodData.dashboard} data={lastGoodData.data} />
            )}
        </Box>
    );
};

export default PreviewDashboard;
