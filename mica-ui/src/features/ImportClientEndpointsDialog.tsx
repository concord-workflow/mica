import { useImportFromClientData } from '../api/clientEndpoint.ts';
import ProfileSelect from './ProfileSelect.tsx';
import { LoadingButton } from '@mui/lab';
import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography,
} from '@mui/material';

import React, { useState } from 'react';
import { useQueryClient } from 'react-query';

interface Props {
    open: boolean;
    onSuccess: () => void;
    onClose: () => void;
}

const ImportClientEndpointsDialog = ({ open, onSuccess, onClose }: Props) => {
    const client = useQueryClient();

    const [profileId, setProfileId] = useState<string>();

    const { mutateAsync, isLoading, error } = useImportFromClientData({
        onSuccess: async () => {
            await client.invalidateQueries(['clientEndpoint']);
        },
    });

    const handleImport = React.useCallback(async () => {
        if (!profileId) {
            return;
        }
        await mutateAsync({ profileId });
        onSuccess();
    }, [profileId, mutateAsync, onSuccess]);

    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>Import Client Endpoints</DialogTitle>
            <DialogContent sx={{ padding: 3 }}>
                {error && (
                    <Alert severity="error" sx={{ marginBottom: 1 }}>
                        {error.message}
                    </Alert>
                )}
                <Typography>
                    Select the profile that will be used to extract the client endpoint information
                    from the available client data.
                </Typography>
                <ProfileSelect
                    value={profileId}
                    onChange={(profileId) => setProfileId(profileId)}
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
                    onClick={handleImport}>
                    Import
                </LoadingButton>
            </DialogActions>
        </Dialog>
    );
};

export default ImportClientEndpointsDialog;
