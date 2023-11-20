import { NewProfile, ProfileEntry, createProfile } from '../api/profile.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import SaveIcon from '@mui/icons-material/Save';
import { Alert, Button, Container, FormControl, Paper, TextField, Typography } from '@mui/material';
import { editor } from 'monaco-editor';

import Editor, { OnMount } from '@monaco-editor/react';
import React from 'react';
import { useMutation } from 'react-query';
import { useNavigate } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>New profile</b> -- TODO.
    </>
);

const NewProfilePage = () => {
    const [editableProfileName, setEditableProfileName] = React.useState<string>('');

    const editorRef = React.useRef<editor.IStandaloneCodeEditor | null>(null);
    const handleEditorOnMount: OnMount = (editor) => {
        editorRef.current = editor;
    };

    const { mutateAsync, isLoading, error } = useMutation<ProfileEntry, Error, NewProfile>(
        createProfile,
    );

    const navigate = useNavigate();
    const handleSave = React.useCallback(async () => {
        const editor = editorRef.current;
        if (!editor) {
            return;
        }

        const value = editor.getValue();
        await mutateAsync({ name: editableProfileName, schema: JSON.parse(value) });
        navigate(`/profile/${editableProfileName}/details`);
    }, [editableProfileName, mutateAsync, navigate]);

    return (
        <>
            <PageTitle help={HELP}>Profile</PageTitle>
            <Container maxWidth="xl">
                <Typography variant="h5" sx={{ marginBottom: 1 }}>
                    <FormControl>
                        <TextField
                            disabled={isLoading}
                            value={editableProfileName}
                            size="small"
                            onChange={(ev) => setEditableProfileName(ev.target.value)}
                        />
                    </FormControl>
                </Typography>
                {error && <Alert severity="error">{error.message}</Alert>}
                <ActionBar>
                    <Spacer />
                    <FormControl>
                        <Button
                            disabled={isLoading}
                            startIcon={<SaveIcon />}
                            variant="contained"
                            onClick={handleSave}>
                            Save
                        </Button>
                    </FormControl>
                </ActionBar>
                <Paper>
                    <Editor
                        loading={isLoading}
                        height="60vh"
                        defaultLanguage="yaml"
                        defaultValue={JSON.stringify({}, null, 2)}
                        onMount={handleEditorOnMount}
                    />
                </Paper>
            </Container>
        </>
    );
};

export default NewProfilePage;
