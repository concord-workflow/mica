import { getProfile, updateProfile } from '../api/profile.ts';
import ActionBar from '../components/ActionBar.tsx';
import EditableLabel from '../components/EditableLabel.tsx';
import PageTitle from '../components/PageTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import SaveIcon from '@mui/icons-material/Save';
import {
    Button,
    CircularProgress,
    Container,
    FormControl,
    Paper,
    Snackbar,
    Typography,
} from '@mui/material';
import { editor } from 'monaco-editor';

import Editor, { OnMount } from '@monaco-editor/react';
import React from 'react';
import { useMutation, useQuery } from 'react-query';
import { useParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Profile Details</b> -- TODO.
    </>
);

type RouteParams = {
    profileName: string;
};

const ProfileDetailsPage = () => {
    const { profileName } = useParams<RouteParams>();
    const [editableProfileName, setEditableProfileName] = React.useState<string>(profileName!);

    const { data, isFetching } = useQuery(
        ['profile', profileName],
        () => getProfile(profileName!),
        {
            enabled: profileName !== undefined,
        },
    );

    const editorRef = React.useRef<editor.IStandaloneCodeEditor | null>(null);
    const handleEditorOnMount: OnMount = (editor) => {
        editorRef.current = editor;
    };

    const { mutateAsync } = useMutation(updateProfile);

    const [openSuccessNotification, setOpenSuccessNotification] = React.useState(false);

    const handleSave = React.useCallback(async () => {
        const editor = editorRef.current;
        if (!editor || !data) {
            return;
        }

        const value = editor.getValue();
        await mutateAsync({ ...data, name: editableProfileName, schema: JSON.parse(value) });
        setOpenSuccessNotification(true);
    }, [data, editableProfileName, mutateAsync]);

    return (
        <>
            <PageTitle help={HELP}>Profile</PageTitle>
            <Snackbar
                open={openSuccessNotification}
                autoHideDuration={5000}
                onClose={() => setOpenSuccessNotification(false)}
                message="Profile saved successfully"
            />
            <Container maxWidth="xl">
                <Typography variant="h5" sx={{ marginBottom: 1 }}>
                    <EditableLabel
                        value={editableProfileName}
                        onChange={(value) => setEditableProfileName(value)}>
                        {editableProfileName} {isFetching && <CircularProgress size={16} />}
                    </EditableLabel>
                </Typography>
                {data && (
                    <>
                        <ActionBar>
                            <Spacer />
                            <FormControl>
                                <Button
                                    startIcon={<SaveIcon />}
                                    variant="contained"
                                    onClick={handleSave}>
                                    Update
                                </Button>
                            </FormControl>
                        </ActionBar>
                        <Paper>
                            <Editor
                                height="60vh"
                                defaultLanguage="yaml"
                                defaultValue={JSON.stringify(data.schema, null, 2)}
                                onMount={handleEditorOnMount}
                            />
                        </Paper>
                    </>
                )}
            </Container>
        </>
    );
};

export default ProfileDetailsPage;
