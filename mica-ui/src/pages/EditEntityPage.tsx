import { MICA_VIEW_KIND, getEntityDoc, kindToTemplate } from '../api/entity.ts';
import { usePutYamlString } from '../api/upload.ts';
import ActionBar from '../components/ActionBar.tsx';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import Spacer from '../components/Spacer.tsx';
import PreviewView from '../features/views/PreviewView.tsx';
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
    styled,
} from '@mui/material';

import Editor from '@monaco-editor/react';
import React, { useEffect } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useQuery } from 'react-query';
import { useBeforeUnload, useNavigate, useParams, useSearchParams } from 'react-router-dom';

type RouteParams = {
    entityId: '_new' | string;
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

const PreviewDrawer = styled(Drawer)(() => ({
    '.MuiPaper-root': {
        minHeight: '40%',
        maxHeight: '40%',
    },
}));

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
    } = useQuery(['entity', 'yaml', entityId], () => getEntityDoc(entityId!), {
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        keepPreviousData: false,
        enabled: entityId !== undefined && entityId !== '_new' && !hasUnsavedChanges,
        onSuccess: (data) => {
            setEditorValue(data);
            setDirty(false);
        },
    });

    // save the entity
    const { mutateAsync, isLoading: isSaving, error: saveError } = usePutYamlString();

    // because we redirect to the new entity, we need to pass the success state via the search params
    const success = searchParams.get('success');
    const [showSuccess, setShowSuccess] = React.useState<boolean>(
        success !== null && success !== undefined,
    );
    const handleSuccessClose = React.useCallback(() => setShowSuccess(false), []);

    // the entity ID and kind can be changed by the user, we need to keep track of them
    const [selectedId, setSelectedId] = React.useState(entityId);
    const [selectedName, setSelectedName] = React.useState(searchParams.get('name') ?? undefined);
    const [selectedKind, setSelectedKind] = React.useState(searchParams.get('kind') ?? undefined);

    const [editorValue, setEditorValue] = React.useState<string>(() => {
        if (selectedId === '_new') {
            if (selectedKind) {
                return kindToTemplate(selectedName ?? '/myEntity', selectedKind);
            } else {
                return '# new entity';
            }
        } else {
            return serverValue ?? '';
        }
    });
    const [dirty, setDirty] = React.useState<boolean>(entityId === '_new');

    // stuff for the live preview feature
    const [showPreview, setShowPreview] = React.useState<boolean>(false);
    const handlePreviewSwitch = React.useCallback((enable: boolean) => {
        setShowPreview(enable);
    }, []);
    const handlePreviewClose = React.useCallback(() => setShowPreview(false), []);

    const handleEditorOnChange = React.useCallback((value: string | undefined) => {
        setEditorValue(value ?? '');
        setDirty(true);
    }, []);

    // sync the editor value to the state
    useEffect(() => {
        const newId = getYamlField(editorValue, 'id');
        setSelectedId((prev) => (prev === newId ? prev : newId));

        const newName = getYamlField(editorValue, 'name');
        setSelectedName((prev) => (prev === newName ? prev : newName));

        const newKind = getYamlField(editorValue, 'kind');
        setSelectedKind((prev) => (prev === newKind ? prev : newKind));
    }, [editorValue]);

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
            }
        }

        setDirty(false);
        setShowSuccess(true);

        // remove unsaved changes from local storage
        localStorage.removeItem(`dirty-${entityId}`);
    }, [entityId, mutateAsync, navigate, refetch, editorValue]);

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
    const handleUnsavedChangesRestoredClose = React.useCallback(
        () => setShowUnsavedChangesRestored(false),
        [],
    );
    useEffect(() => {
        const value = localStorage.getItem(`dirty-${entityId}`);
        if (!value) {
            return;
        }

        setEditorValue(value);
        setDirty(true);
        setShowUnsavedChangesRestored(true);
    }, [entityId]);

    return (
        <>
            <Snackbar
                open={showSuccess}
                autoHideDuration={5000}
                onClose={handleSuccessClose}
                message="Data saved successfully"
            />
            <Snackbar
                open={showUnsavedChangesRestored}
                autoHideDuration={5000}
                onClose={handleUnsavedChangesRestoredClose}
                message="Unsaved changes restored"
            />
            <Box display="flex" flexDirection="column" height="100%">
                <Box margin={2}>
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
                        <PreviewDrawer anchor="bottom" variant="permanent">
                            <PreviewView data={editorValue} onClose={handlePreviewClose} />
                        </PreviewDrawer>
                    )}
                </Box>
            </Box>
        </>
    );
};

export default EditEntityPage;
