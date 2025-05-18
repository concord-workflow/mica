import { CanBeDeletedResponse, Entry, EntryType, canBeDeleted } from '../../api/entityList.ts';
import DeleteIcon from '@mui/icons-material/Delete';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { CircularProgress, IconButton, Menu, MenuItem, Tooltip } from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import React, { useState } from 'react';

interface Props {
    handleDelete: (row: Entry) => void;
    row: Entry;
}

const EntityTableRowMenu = ({ handleDelete, row }: Props) => {
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    const open = Boolean(anchorEl);
    const [deleteStatus, setDeleteStatus] = React.useState<CanBeDeletedResponse>();

    const handleClick = React.useCallback(
        (event: React.MouseEvent<HTMLElement>) => {
            setAnchorEl(event.currentTarget);
            if (row.type === EntryType.FOLDER) {
                setDeleteStatus({ canBeDeleted: true });
            } else {
                canBeDeleted(row.entityId!)
                    .then(setDeleteStatus)
                    .catch((e) => {
                        console.warn("Can't determine if the entry is deletable", e);
                    });
            }
        },
        [row.type, row.entityId],
    );

    const handleClose = () => {
        setAnchorEl(null);
    };

    return (
        <>
            <IconButton onClick={handleClick} size="small">
                <MoreVertIcon />
            </IconButton>
            <Menu anchorEl={anchorEl} open={open} onClose={handleClose}>
                <Tooltip title={deleteStatus?.whyNot}>
                    <span>
                        <MenuItem
                            onClick={() => handleDelete(row)}
                            disabled={!deleteStatus || !deleteStatus.canBeDeleted}>
                            <ListItemIcon>
                                {deleteStatus === undefined && <CircularProgress size="18px" />}
                                {deleteStatus !== undefined && <DeleteIcon fontSize="small" />}
                            </ListItemIcon>
                            <ListItemText>Delete</ListItemText>
                        </MenuItem>
                    </span>
                </Tooltip>
            </Menu>
        </>
    );
};

export default EntityTableRowMenu;
