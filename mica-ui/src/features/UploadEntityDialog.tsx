import { usePutPartialYaml } from '../api/upload.ts';
import ReadableApiError from '../components/ReadableApiError.tsx';
import EntityKindSelect from './EntityKindSelect.tsx';
import { LoadingButton } from '@mui/lab';
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    InputLabel,
    TextField,
    Typography,
} from '@mui/material';

import { useQueryClient } from '@tanstack/react-query';
import React, { useRef } from 'react';

interface Props {
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const UploadEntityDialog = ({ open, onSuccess, onClose }: Props) => {
    const client = useQueryClient();
    const { mutateAsync, isPending, error } = usePutPartialYaml({
        onSuccess: async () => {
            await client.invalidateQueries({ queryKey: ['entity'] });
        },
    });

    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [entityName, setEntityName] = React.useState<string>('');
    const [entityKind, setEntityKind] = React.useState<string>('');

    const handleUpload = React.useCallback(async () => {
        const input = fileInputRef.current;
        if (!input || !input.files || input.files.length < 1) {
            return;
        }
        const file = input.files.item(0);
        if (!file) {
            return;
        }
        await mutateAsync({ file, entityName, entityKind });
        input.value = '';
        onSuccess();
    }, [mutateAsync, onSuccess, entityName, entityKind]);

    return (
        <Dialog open={open} onClose={onClose} fullWidth={true}>
            <DialogTitle>Upload Entities</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {error && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        <ReadableApiError error={error} />
                    </Alert>
                )}
                <FormControl sx={{ mb: 5 }} fullWidth={true}>
                    <FormLabel>YAML file</FormLabel>
                    <input type="file" ref={fileInputRef} />
                </FormControl>
                <Box>
                    <Typography variant="body2" sx={{ mb: 2 }}>
                        You can override "name" and "kind" of the uploaded entity. Otherwise, the
                        values from the file will be used.
                    </Typography>
                    <TextField
                        fullWidth={true}
                        size="small"
                        variant="outlined"
                        label="Entity name"
                        placeholder="/my/entity"
                        sx={{ mb: 2 }}
                        value={entityName}
                        onChange={(ev) => setEntityName(ev.target.value)}
                    />
                    <FormControl fullWidth={true} size="small">
                        <InputLabel>Kind</InputLabel>
                        <EntityKindSelect
                            disableAny={true}
                            value={entityKind}
                            onChange={setEntityKind}
                        />
                    </FormControl>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button variant="contained" color="primary" onClick={onClose}>
                    Cancel
                </Button>
                <LoadingButton
                    loading={isPending}
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
