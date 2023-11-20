import EditIcon from '@mui/icons-material/Edit';
import { FormControl, IconButton, TextField } from '@mui/material';

import React from 'react';

interface Props {
    children: React.ReactNode;
    disabled?: boolean;
    value: string;
    onChange: (value: string) => void;
}

const EditableLabel = ({ children, disabled, value, onChange }: Props) => {
    const [editMode, setEditMode] = React.useState<boolean>(false);

    const handleOnClick = () => {
        setEditMode((mode) => !mode);
    };

    return (
        <>
            {editMode && (
                <FormControl>
                    <TextField
                        size="small"
                        value={value}
                        onChange={(ev) => onChange(ev.target.value)}
                    />
                </FormControl>
            )}
            {!editMode && children}
            {!disabled && (
                <IconButton size="small" onClick={handleOnClick}>
                    <EditIcon />
                </IconButton>
            )}
        </>
    );
};

export default EditableLabel;
