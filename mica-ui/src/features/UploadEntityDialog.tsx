import { usePutYaml } from '../api/entity.ts';
import { LoadingButton } from '@mui/lab';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';

import { useRef } from 'react';
import { useQueryClient } from 'react-query';

interface Props {
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const UploadEntityDialog = ({ open, onSuccess, onClose }: Props) => {
    const client = useQueryClient();
    const { mutateAsync, isLoading, error } = usePutYaml({
        onSuccess: async () => {
            await client.invalidateQueries(['entity']);
        },
    });

    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const handleUpload = async () => {
        const input = fileInputRef.current;
        if (!input || !input.files || input.files.length < 1) {
            return;
        }
        const file = input.files.item(0);
        if (!file) {
            return;
        }
        await mutateAsync({ file });
        input.value = '';
        onSuccess();
    };

    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>Upload Entities</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {error && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        {error.message}
                    </Alert>
                )}
                <input type="file" ref={fileInputRef} />
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
