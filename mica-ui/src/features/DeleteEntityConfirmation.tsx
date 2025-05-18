import { BatchOperation, useApplyBatchOperation } from '../api/batch.ts';
import { useDeleteById } from '../api/entity.ts';
import { EntryType } from '../api/entityList.ts';
import ReadableApiError from '../components/ReadableApiError.tsx';
import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Link,
} from '@mui/material';

import { useQueryClient } from '@tanstack/react-query';
import React from 'react';
import { Link as RouterLink } from 'react-router-dom';

interface Props {
    type: EntryType;
    entityId: string | undefined;
    entityName: string;
    entityPath?: string;
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const DeleteEntityConfirmation = ({
    type,
    entityId,
    entityName,
    entityPath,
    open,
    onSuccess,
    onClose,
}: Props) => {
    const client = useQueryClient();

    const {
        mutateAsync: deleteById,
        isPending: deleteByIdPending,
        error: deleteByIdError,
    } = useDeleteById({
        onSuccess: async () => {
            await client.invalidateQueries({ queryKey: ['entity', 'list'] });
        },
    });

    const {
        mutateAsync: deleteBatch,
        isPending: deleteBatchPending,
        error: deleteBatchError,
    } = useApplyBatchOperation({
        onSuccess: async () => {
            await client.invalidateQueries({ queryKey: ['entity', 'list'] });
        },
    });

    const handleDelete = React.useCallback(async () => {
        switch (type) {
            case EntryType.FILE: {
                await deleteById({ entityId: entityId! });
                break;
            }
            case EntryType.FOLDER: {
                await deleteBatch({
                    operation: BatchOperation.DELETE,
                    namePatterns: [`^${entityPath}/${entityName}/.*$`],
                });
                break;
            }
        }
        onSuccess();
    }, [type, entityId, entityName, entityPath, deleteById, deleteBatch, onSuccess]);

    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>Delete entity?</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {deleteByIdError && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        <ReadableApiError error={deleteByIdError} />
                    </Alert>
                )}
                {deleteBatchError && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        <ReadableApiError error={deleteBatchError} />
                    </Alert>
                )}
                {type === EntryType.FILE && (
                    <>
                        Are you sure you want to delete entity <b>{entityName}</b> (ID: {entityId})?
                        This action cannot be undone (but you can find deleted entities in{' '}
                        <Link component={RouterLink} to="/trash">
                            Trash
                        </Link>
                        ).
                    </>
                )}
                {type === EntryType.FOLDER && (
                    <>
                        Are you sure you want to delete <strong>all</strong> entries under{' '}
                        <b>
                            {entityPath}/{entityName}/**
                        </b>
                        ? This action cannot be undone (but you can find deleted entities in{' '}
                        <Link component={RouterLink} to="/trash">
                            Trash
                        </Link>
                        ).
                    </>
                )}
            </DialogContent>
            <DialogActions>
                <Button variant="contained" color="primary" onClick={onClose}>
                    Cancel
                </Button>
                <Button
                    loading={deleteByIdPending || deleteBatchPending}
                    variant="text"
                    color="error"
                    onClick={handleDelete}>
                    Delete
                </Button>
            </DialogActions>
        </Dialog>
    );
};

export default DeleteEntityConfirmation;
