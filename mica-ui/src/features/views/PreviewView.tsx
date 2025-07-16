import { PartialEntity } from '../../api/entity.ts';
import { ObjectSchemaNode } from '../../api/schema.ts';
import { PreviewViewRequest, usePreview } from '../../api/view.ts';
import ReadableApiError from '../../components/ReadableApiError.tsx';
import DataView from './DataView.tsx';
import ShowRenderedViewDetailsToggle from './ShowRenderedViewDetailsToggle.tsx';
import ViewParameters from './ViewParameters.tsx';
import CloseIcon from '@mui/icons-material/Close';
import { Alert, Box, CircularProgress, Grid, IconButton } from '@mui/material';
import { useDebounce } from '@uidotdev/usehooks';
import { parse as parseYaml } from 'yaml';

import React, { PropsWithChildren } from 'react';

interface PreviewRequestOrError {
    request?: PreviewViewRequest;
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
            },
            parameters,
        };
    } catch (e) {
        return { error: new Error((e as Error).message) };
    }
};

const parseValidationErrors = (entity: PartialEntity | undefined): string[][] | undefined => {
    if (!entity || !entity.validation) {
        return;
    }

    interface Message {
        message: string;
    }

    interface Entry {
        messages: Array<Message>;
    }

    const validation = entity.validation as unknown as Array<Entry>;
    const result = validation
        .map((entry) => entry.messages.map((message) => message.message))
        .filter((messages) => messages.length > 0);

    if (result.length === 0) {
        return;
    }

    return result;
};

const PreviewView = ({ data, onClose }: Props) => {
    const [lastGoodData, setLastGoodData] = React.useState<PartialEntity>();

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
    const {
        request,
        error: requestError,
        parameters,
    } = React.useMemo(() => parseData(debouncedData), [debouncedData]);

    const [requestParameters, setRequestParameters] = React.useState<Record<string, string | null>>(
        {},
    );
    const handleParameterChange = React.useCallback((key: string, value: string) => {
        setRequestParameters((prev) => ({ ...prev, [key]: value === '' ? null : value }));
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
                if (value !== null && validKeys.includes(key)) {
                    acc[key] = value;
                }
                return acc;
            },
            {} as Record<string, string>,
        );

        mutateAsync({ ...request, parameters: validParameters });
    }, [mutateAsync, request, parameters, debouncedRequestParameters]);

    const debouncedIsPending = useDebounce(isPending, 250);

    const [showDetails, setShowDetails] = React.useState(false);
    const handleDetailsToggle = React.useCallback((value: boolean) => {
        setShowDetails(value);
    }, []);

    const validationErrors = parseValidationErrors(lastGoodData);

    return (
        <Grid container height="100%">
            <Grid size={3}>
                <Box>
                    {apiError && (
                        <Alert color="error" sx={{ m: 1 }}>
                            <ReadableApiError error={apiError} />
                        </Alert>
                    )}
                    {requestError && <Alert color="error">{requestError.message}</Alert>}
                    {validationErrors &&
                        validationErrors.map((messages, idx) => (
                            <Alert key={idx} color="warning" title={`Validation error #${idx}`}>
                                Validation failed:
                                <ul>
                                    {messages.map((message, idx) => (
                                        <li key={idx}>{message}</li>
                                    ))}
                                </ul>
                            </Alert>
                        ))}
                </Box>
                <Box margin={1}>
                    <ViewParameters
                        parameters={parameters}
                        values={requestParameters}
                        onChange={handleParameterChange}
                    />
                </Box>
            </Grid>
            <Grid size={9}>
                <Box
                    position="fixed"
                    display="flex"
                    alignItems="center"
                    bottom={(theme) => theme.spacing(2)}
                    right={(theme) => theme.spacing(2)}
                    zIndex={100}>
                    {debouncedIsPending && (
                        <CircularProgress color="secondary" size={24} sx={{ marginRight: 2 }} />
                    )}
                    <ShowRenderedViewDetailsToggle
                        checked={showDetails}
                        onChange={handleDetailsToggle}
                    />
                    <IconButton onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                </Box>
                {requestError && <Overlay />}
                {lastGoodData && <DataView data={showDetails ? lastGoodData : lastGoodData.data} />}
            </Grid>
        </Grid>
    );
};

export default PreviewView;
