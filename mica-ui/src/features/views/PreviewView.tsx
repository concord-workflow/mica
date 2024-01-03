import { PartialEntity } from '../../api/entity.ts';
import { ObjectSchemaNode } from '../../api/schema.ts';
import { PreviewRequest, usePreview } from '../../api/view.ts';
import ReadableApiError from '../../components/ReadableApiError.tsx';
import DataView from './DataView.tsx';
import ShowRenderedViewDetailsToggle from './ShowRenderedViewDetailsToggle.tsx';
import ViewParameters from './ViewParameters.tsx';
import CloseIcon from '@mui/icons-material/Close';
import { Alert, Box, CircularProgress, IconButton } from '@mui/material';
import { useDebounce } from '@uidotdev/usehooks';
import { parse as parseYaml } from 'yaml';

import React, { PropsWithChildren } from 'react';

interface PreviewRequestOrError {
    request?: PreviewRequest;
    parameters?: ObjectSchemaNode;
    error?: Error;
}

interface Props {
    data: string;
    onClose: () => void;
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
        const view = parseYaml(data);

        if (!view.data) {
            return { error: new Error('View "data" is required') };
        }

        if (!view.selector) {
            return { error: new Error('View "selector" is required') };
        }

        const parameters = view.parameters as ObjectSchemaNode;

        // we don't need view ID for preview and invalid IDs will cause errors that we don't care about here
        delete view.id;

        return {
            request: {
                view,
                limit: 10,
            },
            parameters,
        };
    } catch (e) {
        return { error: new Error((e as Error).message) };
    }
};

const PreviewView = ({ data, onClose }: Props) => {
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

    const debouncedData = useDebounce(data, 500);
    const {
        request,
        error: requestError,
        parameters,
    } = React.useMemo(() => parseData(debouncedData), [debouncedData]);

    const [requestParameters, setRequestParameters] = React.useState<Record<string, string>>({});
    const handleParameterChange = React.useCallback((key: string, value: string) => {
        setRequestParameters((prev) => ({ ...prev, [key]: value }));
    }, []);

    const debouncedRequestParameters = useDebounce(requestParameters, 500);
    React.useEffect(() => {
        if (!request) {
            return;
        }

        // pass only those requestParameters that are defined in the view's parameters section
        // (in case someone adds and removed parameters from the view definition, we don't want
        // to pass parameters that are no longer defined)
        const validKeys = Object.keys(parameters?.properties ?? {});
        const validParameters = Object.entries(debouncedRequestParameters).reduce(
            (acc, [key, value]) => {
                if (validKeys.includes(key)) {
                    acc[key] = value;
                }
                return acc;
            },
            {} as Record<string, string>,
        );

        mutateAsync({ ...request, parameters: validParameters });
    }, [mutateAsync, request, parameters, debouncedRequestParameters]);

    const debouncedIsLoading = useDebounce(isLoading, 250);

    const [showDetails, setShowDetails] = React.useState(false);
    const handleDetailsToggle = React.useCallback((value: boolean) => {
        setShowDetails(value);
    }, []);

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
                <Box position="absolute" right={0}>
                    <Box display="flex" justifyContent="flex-end" marginBottom={1}>
                        <ShowRenderedViewDetailsToggle
                            checked={showDetails}
                            onChange={handleDetailsToggle}
                        />
                        <IconButton onClick={onClose}>
                            <CloseIcon />
                        </IconButton>
                    </Box>
                    <ViewParameters
                        parameters={parameters}
                        values={requestParameters}
                        onChange={handleParameterChange}
                    />
                </Box>
                {debouncedIsLoading && (
                    <Overlay>
                        <CircularProgress color="secondary" />
                    </Overlay>
                )}
                {requestError && <Overlay />}
                {lastGoodData && <DataView data={showDetails ? lastGoodData : lastGoodData.data} />}
            </Box>
        </>
    );
};

export default PreviewView;
