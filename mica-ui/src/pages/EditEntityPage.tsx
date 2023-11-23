import { getEntityAsYamlString, usePutYamlString } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import SaveIcon from '@mui/icons-material/Save';
import { Alert, Box, Button, FormControl } from '@mui/material';
import { editor } from 'monaco-editor';

import Editor, { OnMount } from '@monaco-editor/react';
import React, { useEffect } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useQuery } from 'react-query';
import { useParams } from 'react-router-dom';

type RouteParams = {
    entityId: string;
};

const HELP: React.ReactNode = (
    <>
        <b>Entity Editor</b> -- TODO.
    </>
);

const EditEntityPage = () => {
    const { entityId } = useParams<RouteParams>();

    const { data, isLoading } = useQuery(
        ['entity', entityId],
        () => getEntityAsYamlString(entityId!),
        {
            keepPreviousData: false,
            enabled: entityId !== undefined && entityId !== '_new',
        },
    );

    const editorRef = React.useRef<editor.IStandaloneCodeEditor | null>(null);
    const handleEditorOnMount: OnMount = (editor) => {
        editorRef.current = editor;
    };

    const { mutateAsync, isLoading: isSaving, error } = usePutYamlString();
    const handleSave = React.useCallback(async () => {
        const editor = editorRef.current;
        if (!editor) {
            return;
        }

        const value = editor.getValue();
        await mutateAsync({ body: value });
    }, [mutateAsync]);

    const defaultValue = entityId === '_new' ? '# new entity' : data;

    const [ready, setReady] = React.useState<boolean>(false);
    useEffect(() => {
        const timer = setTimeout(() => setReady(true), 100);
        return () => clearTimeout(timer);
    });

    return (
        <Box display="flex" flexDirection="column" height="100%">
            <Box sx={{ m: 2 }}>
                <ActionBar>
                    <PageTitle help={HELP}>Entity</PageTitle>
                    <Spacer />
                    <FormControl>
                        <Button
                            disabled={isLoading || isSaving}
                            startIcon={<SaveIcon />}
                            variant="contained"
                            onClick={handleSave}>
                            Save
                        </Button>
                    </FormControl>
                </ActionBar>
                {error && <Alert severity="error">{error.message}</Alert>}
            </Box>
            <Box flexGrow="1">
                <ErrorBoundary
                    fallback={<b>Something went wrong while trying to render the editor.</b>}>
                    {ready && defaultValue && (
                        <Editor
                            loading={isLoading || isSaving}
                            height="100%"
                            defaultLanguage="yaml"
                            defaultValue={defaultValue}
                            onMount={handleEditorOnMount}
                        />
                    )}
                </ErrorBoundary>
            </Box>
        </Box>
    );
};

export default EditEntityPage;
