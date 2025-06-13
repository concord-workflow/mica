import { EntryType } from '../api/entityList.ts';
import DeleteEntityConfirmation from './DeleteEntityConfirmation.tsx';
import DeleteIcon from '@mui/icons-material/Delete';
import { Button, FormControl, Tooltip } from '@mui/material';

import React from 'react';

interface Props {
    entityId: string;
    entityName: string;
    disabled: boolean;
    onSuccess: () => void;
}

const DeleteEntityButton = ({ entityId, entityName, disabled, onSuccess }: Props) => {
    const [openDeleteConfirmation, setOpenDeleteConfirmation] = React.useState(false);

    return (
        <>
            <DeleteEntityConfirmation
                type={EntryType.FILE}
                entityId={entityId}
                entityName={entityName}
                open={openDeleteConfirmation}
                onSuccess={() => {
                    setOpenDeleteConfirmation(false);
                    onSuccess();
                }}
                onClose={() => setOpenDeleteConfirmation(false)}
            />
            <FormControl>
                <Tooltip title="Permanently delete this entity. This action cannot be undone.">
                    <span>
                        <Button
                            startIcon={<DeleteIcon />}
                            variant="outlined"
                            color="error"
                            onClick={() => setOpenDeleteConfirmation(true)}
                            disabled={disabled}>
                            Delete
                        </Button>
                    </span>
                </Tooltip>
            </FormControl>
        </>
    );
};

export default DeleteEntityButton;
