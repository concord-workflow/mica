import { CurrentUser, useCurrentUser } from '../UserContext.tsx';
import { ApiKeyEntry, listApiKeys, useCreateApiKey, useDeleteApiKey } from '../api/apiKey.ts';
import { ApiError } from '../api/error.ts';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import KeyIcon from '@mui/icons-material/Key';
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
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
    IconButton,
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

const DeleteApiKeyButton = ({
    apiKeyId,
    onSuccess,
}: {
    apiKeyId: string;
    onSuccess: () => void;
}) => {
    const queryClient = useQueryClient();

    const { mutateAsync, isPending } = useDeleteApiKey({
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apikey'] }).then(onSuccess),
    });

    const [showConfirmation, setShowConfirmation] = React.useState<boolean>(false);

    const handleClick = React.useCallback(() => {
        setShowConfirmation(true);
    }, []);

    const handleConfirm = React.useCallback(() => {
        mutateAsync({ id: apiKeyId });
    }, [apiKeyId, mutateAsync]);

    if (showConfirmation) {
        return (
            <Button onClick={handleConfirm} loading={isPending} color="error" variant="outlined">
                Remove permanently
            </Button>
        );
    }

    return (
        <IconButton onClick={handleClick}>
            <RemoveCircleOutlineIcon />
        </IconButton>
    );
};

interface Props {
    open: boolean;
    onClose: () => void;
    user: CurrentUser;
}

const ApiKeyDialog = ({ open, onClose }: Props) => {
    const currentUser = useCurrentUser();

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
        reset,
    } = useCreateApiKey({
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['apikey'] }),
    });

    const [apiKeyName, setApiKeyName] = React.useState<string>('');
    const handleCreate = React.useCallback(() => {
        if (currentUser.userId == null) {
            return;
        }
        mutateAsync({ userId: currentUser.userId, name: apiKeyName });
        setApiKeyName('');
    }, [currentUser.userId, apiKeyName, mutateAsync]);

    const error = createError || listError;
    const isError = isCreateError || isListError;

    return (
        <Dialog open={open} onClose={onClose} fullWidth={true} maxWidth="md">
            <DialogTitle>
                <Box display="flex" alignItems="center">
                    <KeyIcon sx={{ mr: 2 }} /> API Keys
                </Box>
            </DialogTitle>
            <DialogContent>
                {createdKey && (
                    <Alert color="success">
                        New API key created. Save the key, it will be shown only once:{' '}
                        <strong>{createdKey.key}</strong>{' '}
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
                    {!isFetching && existingKeys && existingKeys.length > 0 && (
                        <>
                            <ListSubheader>Existing API keys</ListSubheader>
                            {existingKeys?.map((entry) => (
                                <ListItem
                                    key={entry.id}
                                    secondaryAction={
                                        <DeleteApiKeyButton apiKeyId={entry.id} onSuccess={reset} />
                                    }>
                                    <ListItemText>
                                        <strong>{entry.name}</strong>{' '}
                                        <Typography color="textDisabled">
                                            (ID: {entry.id})
                                        </Typography>
                                    </ListItemText>
                                </ListItem>
                            ))}
                        </>
                    )}

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
