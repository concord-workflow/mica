import { MICA_RECORD_KIND, getEntityAsYamlString, usePutYamlString } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import SaveIcon from '@mui/icons-material/Save';
import { Alert, Box, Button, FormControl, Snackbar } from '@mui/material';
import { editor } from 'monaco-editor';

import Editor, { OnMount } from '@monaco-editor/react';
import React, { useEffect } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useQuery } from 'react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

type RouteParams = {
    entityId: string;
};

const NEW_ENTITY_TEMPLATE = `# new entity
name: myEntity
kind: %%KIND%%
data:
  myProperty: true`;

const HELP: React.ReactNode = (
    <>
        <b>Entity Editor</b> &mdash; edit the entity and click "Save".
        <p />
        Remove the <b>id</b> field if you wish to save the document as a new entity.
    </>
);

const EditEntityPage = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const { entityId } = useParams<RouteParams>();

    const { data, isFetching, refetch } = useQuery(
        ['entity', 'yaml', entityId],
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

        // save and get the new version
        const version = await mutateAsync({ body: editor.getValue() });

        if (entityId === '_new') {
            // update the URL, if it's a "_new" entity then the user will be redirected to the created entity
            navigate(`/entity/${version.id}/edit?success`, { replace: true });
        } else {
            // if it's an existing entity, we need to re-fetch the data to update the editor
            const result = await refetch();
            if (!result.data) {
                throw new Error('Failed to fetch entity after save');
            }
            editor.setValue(result.data);
            setShowSuccess(true);
        }
    }, [entityId, mutateAsync, navigate, refetch]);

    // because we redirect to the new entity, we need to pass the success state via the search params
    const success = searchParams.get('success');
    const [showSuccess, setShowSuccess] = React.useState<boolean>(
        success !== null && success !== undefined,
    );

    const selectedKind = searchParams.get('kind') ?? MICA_RECORD_KIND;
    const editorValue =
        entityId === '_new' ? NEW_ENTITY_TEMPLATE.replace('%%KIND%%', selectedKind) : data;

    // delay the editor rendering as a workaround for monaco-react bug
    const [ready, setReady] = React.useState<boolean>(false);
    useEffect(() => {
        const timer = setTimeout(() => setReady(true), 100);
        return () => clearTimeout(timer);
    });

    return (
        <>
            <Snackbar
                open={showSuccess}
                autoHideDuration={5000}
                onClose={() => setShowSuccess(false)}
                message="Data saved successfully"
            />
            <Box display="flex" flexDirection="column" height="100%">
                <Box sx={{ m: 2 }}>
                    <ActionBar>
                        <PageTitle help={HELP}>Entity</PageTitle>
                        <Spacer />
                        <FormControl>
                            <Button
                                disabled={isFetching || isSaving}
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
                        {ready && editorValue && (
                            <Editor
                                loading={isFetching || isSaving}
                                height="100%"
                                defaultLanguage="yaml"
                                defaultValue={editorValue}
                                onMount={handleEditorOnMount}
                            />
                        )}
                    </ErrorBoundary>
                </Box>
            </Box>
        </>
    );
};

export default EditEntityPage;
