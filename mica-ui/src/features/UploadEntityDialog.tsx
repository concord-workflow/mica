import { usePutPartialYaml } from '../api/upload.ts';
import { LoadingButton } from '@mui/lab';
import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    TextField,
} from '@mui/material';

import React, { useRef } from 'react';
import { useQueryClient } from 'react-query';

interface Props {
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const UploadEntityDialog = ({ open, onSuccess, onClose }: Props) => {
    const client = useQueryClient();
    const { mutateAsync, isLoading, error } = usePutPartialYaml({
        onSuccess: async () => {
            await client.invalidateQueries(['entity']);
        },
    });

    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [entityName, setEntityName] = React.useState<string>('');

    const handleUpload = React.useCallback(async () => {
        const input = fileInputRef.current;
        if (!input || !input.files || input.files.length < 1) {
            return;
        }
        const file = input.files.item(0);
        if (!file) {
            return;
        }
        await mutateAsync({ file, entityName });
        input.value = '';
        onSuccess();
    }, [mutateAsync, onSuccess, entityName]);

    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>Upload Entities</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {error && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        {error.message}
                    </Alert>
                )}
                <FormControl sx={{ mb: 2 }} fullWidth={true}>
                    <FormLabel>YAML file</FormLabel>
                    <input type="file" ref={fileInputRef} />
                </FormControl>
                <TextField
                    fullWidth={true}
                    size="small"
                    variant="outlined"
                    label="Entity name"
                    helperText="Optional. If not specified, the 'name' field from the file will be used."
                    value={entityName}
                    onChange={(ev) => setEntityName(ev.target.value)}
                />
            </DialogContent>
            <DialogActions>
                <Button variant="contained" color="primary" onClick={onClose}>
                    Cancel
                </Button>
                <LoadingButton
                    loading={isLoading}
                    variant="text"
                    color="error"
                    onClick={handleUpload}>
                    Upload
                </LoadingButton>
            </DialogActions>
        </Dialog>
    );
};

export default UploadEntityDialog;
