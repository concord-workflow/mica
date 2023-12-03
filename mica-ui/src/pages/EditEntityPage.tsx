import { MICA_VIEW_KIND, getEntityAsYamlString, kindToTemplate } from '../api/entity.ts';
import { usePutYamlString } from '../api/upload.ts';
import { PreviewRequest } from '../api/view.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import PreviewView from '../features/PreviewView.tsx';
import SaveIcon from '@mui/icons-material/Save';
import {
    Alert,
    Box,
    Button,
    Drawer,
    FormControl,
    FormControlLabel,
    Snackbar,
    Switch,
} from '@mui/material';
import { editor } from 'monaco-editor';
import { parse as parseYaml } from 'yaml';

import Editor, { OnMount } from '@monaco-editor/react';
import React, { useEffect } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useQuery } from 'react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

type RouteParams = {
    entityId: string;
};

const HELP: React.ReactNode = (
    <>
        <b>Entity Editor</b> &mdash; edit the entity and click "Save".
        <p />
        Remove the <b>id</b> field if you wish to save the document as a new entity. Feel free to
        change the entity's <b>kind</b>, the entity will be validated against the new kind.
    </>
);

const getYamlField = (yaml: string | undefined, key: string): string | undefined => {
    if (!yaml) {
        return;
    }
    let start = yaml.lastIndexOf('\n' + key);
    if (start < 0 && yaml.substring(0, key.length) === key) {
        start = 0;
    }
    if (start < 0) {
        return;
    }
    let end = yaml.indexOf('\n', start + 1);
    if (end < 0) {
        end = yaml.length;
    }
    const result = yaml.substring(start + key.length + 2, end).trim();
    if (result.length == 0) {
        return;
    }
    return result;
};

const EditEntityPage = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // load the entity
    const { entityId } = useParams<RouteParams>();
    const { data, isFetching, refetch } = useQuery(
        ['entity', 'yaml', entityId],
        () => getEntityAsYamlString(entityId!),
        {
            refetchOnWindowFocus: false,
            refetchOnReconnect: false,
            keepPreviousData: false,
            enabled: entityId !== undefined && entityId !== '_new',
        },
    );

    // editor harness
    const editorRef = React.useRef<editor.IStandaloneCodeEditor | null>(null);
    const handleEditorOnMount: OnMount = (editor) => {
        editorRef.current = editor;
    };

    const [dirty, setDirty] = React.useState<boolean>(false);

    // save the entity
    const { mutateAsync, isLoading: isSaving, error: saveError } = usePutYamlString();
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
            setDirty(false);
            setShowSuccess(true);
        }
    }, [entityId, mutateAsync, navigate, refetch]);

    // because we redirect to the new entity, we need to pass the success state via the search params
    const success = searchParams.get('success');
    const [showSuccess, setShowSuccess] = React.useState<boolean>(
        success !== null && success !== undefined,
    );

    // stuff for the live preview feature
    const [showPreview, setShowPreview] = React.useState<boolean>(false);
    const [yamlParseError, setYamlParseError] = React.useState<string | undefined>();
    const createPreviewRequest: () => PreviewRequest | undefined = React.useCallback(() => {
        const editor = editorRef.current;
        if (!editor) {
            return;
        }
        const yaml = editor.getValue();
        try {
            const view = parseYaml(yaml);
            setYamlParseError(undefined);
            return {
                view,
                limit: 10,
            };
        } catch (e) {
            console.log('Error parsing YAML:', e);
            setYamlParseError((e as Error).message);
        }
    }, [editorRef]);
    const [previewRequest, setPreviewRequest] = React.useState<PreviewRequest | undefined>(() =>
        createPreviewRequest(),
    );

    // the entity ID and kind can be changed by the user, we need to keep track of them
    const [selectedId, setSelectedId] = React.useState(entityId);
    const [selectedName, setSelectedName] = React.useState<string | undefined>();
    const [selectedKind, setSelectedKind] = React.useState(searchParams.get('kind'));
    const handleOnChange = React.useCallback(
        (value: string | undefined) => {
            setDirty(value !== data);
            setSelectedId((prev) => getYamlField(value, 'id') ?? prev);
            setSelectedName((prev) => getYamlField(value, 'name') ?? prev);
            setSelectedKind((prev) => getYamlField(value, 'kind') ?? prev);
            if (showPreview) {
                setPreviewRequest(createPreviewRequest());
            }
        },
        [data, createPreviewRequest, showPreview],
    );
    useEffect(() => {
        if (!data) {
            return;
        }
        handleOnChange(data);
    }, [handleOnChange, data]);

    // provide a default value for the editor
    const defaultValue =
        selectedId === '_new' ? (selectedKind ? kindToTemplate(selectedKind) : undefined) : data;

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
                        <PageTitle help={HELP}>{selectedName}</PageTitle>
                        <Spacer />
                        {selectedKind === MICA_VIEW_KIND && (
                            <FormControl>
                                <FormControlLabel
                                    control={
                                        <Switch
                                            value={showPreview}
                                            onChange={(ev) => setShowPreview(ev.target.checked)}
                                        />
                                    }
                                    label="Preview"
                                />
                            </FormControl>
                        )}
                        <FormControl>
                            <Button
                                disabled={isFetching || isSaving || !dirty}
                                startIcon={<SaveIcon />}
                                variant="contained"
                                onClick={handleSave}>
                                Save
                            </Button>
                        </FormControl>
                    </ActionBar>
                    {saveError && <Alert severity="error">{saveError.message}</Alert>}
                    {yamlParseError && <Alert severity="error">{yamlParseError}</Alert>}
                </Box>
                <Box flex="1">
                    <ErrorBoundary
                        fallback={<b>Something went wrong while trying to render the editor.</b>}>
                        {defaultValue && (
                            <Editor
                                loading={isFetching || isSaving}
                                height="100%"
                                defaultLanguage="yaml"
                                options={{
                                    minimap: { enabled: false },
                                }}
                                defaultValue={defaultValue}
                                onMount={handleEditorOnMount}
                                onChange={handleOnChange}
                            />
                        )}
                    </ErrorBoundary>
                    {showPreview && (
                        <Drawer anchor="bottom" variant="permanent">
                            {previewRequest && <PreviewView request={previewRequest} />}
                        </Drawer>
                    )}
                </Box>
            </Box>
        </>
    );
};

export default EditEntityPage;
