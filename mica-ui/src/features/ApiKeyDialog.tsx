import { CurrentUser } from '../UserContext.tsx';
import { ApiKeyEntry, listApiKeys, useCreateApiKey } from '../api/apiKey.ts';
import { ApiError } from '../api/error.ts';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    List,
    ListItem,
    ListSubheader,
    Stack,
    TextField,
    Typography,
} from '@mui/material';
import ListItemText from '@mui/material/ListItemText';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import React from 'react';

interface Props {
    open: boolean;
    onClose: () => void;
    user: CurrentUser;
}

const ApiKeyDialog = ({ open, onClose }: Props) => {
    const {
        data: existingKeys,
        error: listError,
        isFetching,
        isError: isListError,
    } = useQuery<Array<ApiKeyEntry>, ApiError>({
        queryKey: ['apikey'],
        queryFn: listApiKeys,
    });

    const queryClient = useQueryClient();
    const {
        mutateAsync,
        data: createdKey,
        error: createError,
        isPending: isCreating,
        isError: isCreateError,
    } = useCreateApiKey({
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apikey'] }),
    });

    const [apiKeyName, setApiKeyName] = React.useState<string>('');
    const handleCreate = React.useCallback(() => {
        mutateAsync({ name: apiKeyName });
        setApiKeyName('');
    }, [apiKeyName, mutateAsync]);

    const error = createError || listError;
    const isError = isCreateError || isListError;

    return (
        <Dialog open={open} onClose={onClose} fullWidth={true}>
            <DialogTitle>API Keys</DialogTitle>
            <DialogContent>
                {createdKey && (
                    <Alert color="success">
                        New API key created: {createdKey.key}{' '}
                        <CopyToClipboardButton text={createdKey.key} />
                    </Alert>
                )}
                {isError && <ReadableApiError error={error} />}
                {isFetching && (
                    <Box display="flex" justifyContent="center" m={4}>
                        <CircularProgress />
                    </Box>
                )}
                <List>
                    <ListSubheader>Existing API keys</ListSubheader>
                    {!isFetching &&
                        existingKeys?.map((entry) => (
                            <ListItem key={entry.id}>
                                <ListItemText>
                                    <strong>{entry.name}</strong> (ID: {entry.id})
                                </ListItemText>
                            </ListItem>
                        ))}
                    {!isFetching && existingKeys && existingKeys.length === 0 && (
                        <ListItem>Current user has no API keys registered.</ListItem>
                    )}
                </List>
                <Divider sx={{ mt: 2, mb: 2 }} />
                <Typography>Create a new API key:</Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                    <TextField
                        disabled={isCreating}
                        label="Name"
                        required={true}
                        size="small"
                        value={apiKeyName}
                        onChange={(ev) => setApiKeyName(ev.target.value)}
                    />
                    <Button
                        loading={isCreating}
                        variant="outlined"
                        color="secondary"
                        onClick={handleCreate}>
                        Create
                    </Button>
                </Stack>
                <DialogActions>
                    <Button variant="contained" color="primary" onClick={onClose}>
                        Close
                    </Button>
                </DialogActions>
            </DialogContent>
        </Dialog>
    );
};

export default ApiKeyDialog;
