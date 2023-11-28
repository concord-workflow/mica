import { EntityEntry, useDeleteById } from '../api/entity.ts';
import { LoadingButton } from '@mui/lab';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';

import React from 'react';
import { useQueryClient } from 'react-query';

interface Props {
    entry: EntityEntry;
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const DeleteEntityConfirmation = ({ entry, open, onSuccess, onClose }: Props) => {
    const client = useQueryClient();
    const { mutateAsync, isLoading, error } = useDeleteById({
        onSuccess: async () => {
            await client.invalidateQueries(['entity'], { refetchActive: false });
        },
    });

    const handleDelete = React.useCallback(async () => {
        await mutateAsync({ entityId: entry.id });
        onSuccess();
    }, [entry.id, mutateAsync, onSuccess]);

    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>Delete entity?</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {error && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        {error.message}
                    </Alert>
                )}
                Are you sure you want to delete entity <b>{entry.name}</b> (ID: {entry.id})?
            </DialogContent>
            <DialogActions>
                <Button variant="contained" color="primary" onClick={onClose}>
                    Cancel
                </Button>
                <LoadingButton
                    loading={isLoading}
                    variant="text"
                    color="error"
                    onClick={handleDelete}>
                    Delete
                </LoadingButton>
            </DialogActions>
        </Dialog>
    );
};

export default DeleteEntityConfirmation;
