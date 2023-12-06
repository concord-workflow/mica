import { useDeleteById } from '../api/entity.ts';
import ReadableApiError from '../components/ReadableApiError.tsx';
import { LoadingButton } from '@mui/lab';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';

import React from 'react';
import { useQueryClient } from 'react-query';

interface Props {
    entityId: string;
    entityName: string;
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const DeleteEntityConfirmation = ({ entityId, entityName, open, onSuccess, onClose }: Props) => {
    const client = useQueryClient();
    const { mutateAsync, isLoading, error } = useDeleteById({
        onSuccess: async () => {
            await client.invalidateQueries(['entity', 'list']);
        },
    });

    const handleDelete = React.useCallback(async () => {
        await mutateAsync({ entityId });
        onSuccess();
    }, [entityId, mutateAsync, onSuccess]);

    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>Delete entity?</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {error && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        <ReadableApiError error={error} />
                    </Alert>
                )}
                Are you sure you want to delete entity <b>{entityName}</b> (ID: {entityId})?
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
