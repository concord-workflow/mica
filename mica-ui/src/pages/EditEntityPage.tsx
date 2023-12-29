import { MICA_VIEW_KIND, getEntityAsYamlString, kindToTemplate } from '../api/entity.ts';
import { usePutYamlString } from '../api/upload.ts';
import ActionBar from '../components/ActionBar.tsx';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import Spacer from '../components/Spacer.tsx';
import PreviewView, { PreviewRequestOrError } from '../features/PreviewView.tsx';
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
import { parse as parseYaml } from 'yaml';

import Editor from '@monaco-editor/react';
import React, { useEffect } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useQuery } from 'react-query';
import { useBeforeUnload, useNavigate, useParams, useSearchParams } from 'react-router-dom';

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

const getYamlField = (yaml: string, key: string): string | undefined => {
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

type Setter = (prev: string | undefined) => string | undefined;

const updateFromYamlField =
    (yaml: string, key: string): Setter =>
    (prev) =>
        getYamlField(yaml, key) ?? prev;

const EditEntityPage = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // TODO handle load errors
    // load the entity
    const { entityId } = useParams<RouteParams>();
    const hasUnsavedChanges = localStorage.getItem(`dirty-${entityId}`) !== null;
    const {
        data: serverValue,
        isLoading,
        isFetching,
        refetch,
    } = useQuery(['entity', 'yaml', entityId], () => getEntityAsYamlString(entityId!), {
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        keepPreviousData: false,
        enabled: entityId !== undefined && entityId !== '_new' && !hasUnsavedChanges,
        onSuccess: (data) => {
            syncValueToState(data);
            setDirty(false);
        },
    });

    const [dirty, setDirty] = React.useState<boolean>(entityId === '_new');

    // save the entity
    const { mutateAsync, isLoading: isSaving, error: saveError } = usePutYamlString();

    // because we redirect to the new entity, we need to pass the success state via the search params
    const success = searchParams.get('success');
    const [showSuccess, setShowSuccess] = React.useState<boolean>(
        success !== null && success !== undefined,
    );

    // the entity ID and kind can be changed by the user, we need to keep track of them
    const [selectedId, setSelectedId] = React.useState(entityId);
    const [selectedName, setSelectedName] = React.useState(searchParams.get('name') ?? undefined);
    const [selectedKind, setSelectedKind] = React.useState(searchParams.get('kind') ?? undefined);

    // provide the default value for the editor
    let defaultValue: string;
    if (selectedId === '_new') {
        if (selectedKind) {
            defaultValue = kindToTemplate(selectedName ?? '/myEntity', selectedKind);
        } else {
            defaultValue = '# new entity';
        }
    } else {
        defaultValue = serverValue ?? '';
    }
    const [editorValue, setEditorValue] = React.useState<string>(defaultValue);

    // we use a counter to trigger a preview refresh when needed
    const [previewVersion, setPreviewVersion] = React.useState<number>(0);
    const previewRequestFn = React.useCallback((): PreviewRequestOrError => {
        if (!editorValue || editorValue.length < 1) {
            return {};
        }
        try {
            const view = parseYaml(editorValue);

            // we don't need view ID for preview and invalid IDs will cause errors that we don't care about here
            delete view.id;

            return {
                request: {
                    view,
                    limit: 10,
                },
            };
        } catch (e) {
            return { error: new Error((e as Error).message) };
        }
    }, [editorValue]);

    // stuff for the live preview feature
    const [showPreview, setShowPreview] = React.useState<boolean>(false);

    const handlePreviewSwitch = React.useCallback((enable: boolean) => {
        setShowPreview(enable);
        if (enable) {
            setPreviewVersion((prev) => prev + 1);
        } else {
            setPreviewVersion((prev) => prev + 1);
        }
    }, []);

    const syncValueToState = React.useCallback((value: string | undefined) => {
        setEditorValue(value ?? '');

        if (!value) {
            return;
        }

        setSelectedId(updateFromYamlField(value, 'id'));
        setSelectedName(updateFromYamlField(value, 'name'));
        setSelectedKind(updateFromYamlField(value, 'kind'));

        setPreviewVersion((prev) => prev + 1);
    }, []);

    const handleEditorOnChange = React.useCallback(
        (value: string | undefined) => {
            syncValueToState(value);
            setDirty(true);
        },
        [syncValueToState],
    );

    const handleSave = React.useCallback(async () => {
        // save and get the new version
        const version = await mutateAsync({ body: editorValue });

        if (entityId === '_new') {
            // update the URL
            navigate(`/entity/${version.id}/edit?success`, { replace: true });
        } else {
            const { data, error } = await refetch();
            if (error) {
                console.error("Couldn't re-fetch the entity:", error);
                return;
            }
            if (data) {
                setEditorValue(data);
                syncValueToState(data);
            }
        }

        setDirty(false);
        setShowSuccess(true);

        // remove unsaved changes from local storage
        localStorage.removeItem(`dirty-${entityId}`);
    }, [entityId, mutateAsync, navigate, refetch, syncValueToState, editorValue]);

    // on the first load, sync the default value to the state
    useEffect(() => {
        syncValueToState(defaultValue);
    }, [syncValueToState, defaultValue]);

    // save any changes to local storage before navigating away
    useBeforeUnload(
        React.useCallback(() => {
            if (!dirty) {
                return;
            }
            console.log('localStorage.setItem', { editorValue });
            localStorage.setItem(`dirty-${entityId}`, editorValue);
        }, [editorValue, entityId, dirty]),
    );

    // load any unsaved changes from local storage (except for the new entities)
    const [showUnsavedChangesRestored, setShowUnsavedChangesRestored] =
        React.useState<boolean>(false);
    useEffect(() => {
        const value = localStorage.getItem(`dirty-${entityId}`);
        if (!value) {
            return;
        }

        syncValueToState(value);

        setDirty(true);
        setShowUnsavedChangesRestored(true);
    }, [entityId, syncValueToState]);

    return (
        <>
            <Snackbar
                open={showSuccess}
                autoHideDuration={5000}
                onClose={() => setShowSuccess(false)}
                message="Data saved successfully"
            />
            <Snackbar
                open={showUnsavedChangesRestored}
                autoHideDuration={5000}
                onClose={() => setShowUnsavedChangesRestored(false)}
                message="Unsaved changes restored"
            />
            <Box display="flex" flexDirection="column" height="100%">
                <Box sx={{ m: 2 }}>
                    <ActionBar>
                        <PageTitle help={HELP}>
                            {selectedName && (
                                <>
                                    <PathBreadcrumbs path={selectedName} />
                                    <CopyToClipboardButton text={selectedName} />
                                </>
                            )}
                        </PageTitle>
                        <Spacer />
                        {selectedKind === MICA_VIEW_KIND && (
                            <FormControl>
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={showPreview}
                                            onChange={(ev) =>
                                                handlePreviewSwitch(ev.target.checked)
                                            }
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
                    {saveError && (
                        <Alert severity="error">
                            <ReadableApiError error={saveError} />
                        </Alert>
                    )}
                </Box>
                <Box flex="1">
                    <ErrorBoundary
                        fallback={<b>Something went wrong while trying to render the editor.</b>}>
                        <Editor
                            loading={isLoading || isFetching || isSaving}
                            height="100%"
                            defaultLanguage="yaml"
                            options={{
                                minimap: { enabled: false },
                            }}
                            value={editorValue}
                            onChange={handleEditorOnChange}
                        />
                    </ErrorBoundary>
                    {showPreview && (
                        <Drawer
                            anchor="bottom"
                            variant="permanent"
                            sx={{
                                '.MuiPaper-root': {
                                    minHeight: '40%',
                                    maxHeight: '40%',
                                },
                            }}>
                            <PreviewView
                                version={previewVersion}
                                requestFn={previewRequestFn}
                                onClose={() => handlePreviewSwitch(false)}
                            />
                        </Drawer>
                    )}
                </Box>
            </Box>
        </>
    );
};

export default EditEntityPage;
