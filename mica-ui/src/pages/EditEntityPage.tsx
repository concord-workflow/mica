import {
    MICA_DASHBOARD_KIND,
    MICA_VIEW_KIND,
    getEntityDoc,
    kindToTemplate,
} from '../api/entity.ts';
import { usePutYamlString } from '../api/upload.ts';
import ActionBar from '../components/ActionBar.tsx';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import Spacer from '../components/Spacer.tsx';
import ResetButton from '../features/editor/ResetButton.tsx';
import YamlEditor from '../features/editor/YamlEditor.tsx';
import PreviewView from '../features/views/PreviewView.tsx';
import PreviewIcon from '@mui/icons-material/Preview';
import SaveIcon from '@mui/icons-material/Save';
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Drawer,
    FormControl,
    FormControlLabel,
    Link,
    Snackbar,
    Switch,
    Tooltip,
    styled,
} from '@mui/material';

import { useQuery } from '@tanstack/react-query';
import React, { useEffect, useMemo } from 'react';
import {
    Link as RouterLink,
    useBeforeUnload,
    useLocation,
    useNavigate,
    useParams,
    useSearchParams,
} from 'react-router-dom';

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

const stripQuotes = (path: string) => {
    if (path.startsWith('"') || path.startsWith("'")) {
        path = path.slice(1);
    }
    if (path.endsWith('"') || path.endsWith("'")) {
        path = path.slice(0, -1);
    }
    return path;
};

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
    return stripQuotes(result);
};

const PreviewDrawer = styled(Drawer)(() => ({
    '.MuiPaper-root': {
        height: '40%',
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
    } = useQuery({
        queryKey: ['entity', 'yaml', entityId],
        queryFn: async () => {
            const doc = await getEntityDoc(entityId!);
            if (!hasUnsavedChanges) {
                setEditorValue(doc);
                setDirty(false);
            }
            return doc;
        },
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        enabled: entityId !== undefined && entityId !== '_new',
    });

    // save the entity
    const { mutateAsync, isPending: isSaving, error: saveError } = usePutYamlString();

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
    const handlePreviewClose = React.useCallback(() => setShowPreview(false), []);
    const handlePreviewSwitch = React.useCallback(
        (ev: React.ChangeEvent<HTMLInputElement>) => setShowPreview(ev.target.checked),
        [],
    );

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

        if (showPreview && newKind !== MICA_VIEW_KIND) {
            setShowPreview(false);
        }
    }, [editorValue, showPreview]);

    const handleSave = React.useCallback(
        async (_ev: unknown, overwrite?: boolean) => {
            // save and get the new version
            const version = await mutateAsync({ body: editorValue, overwrite: overwrite ?? false });

            if (selectedId === '_new' || selectedId === undefined) {
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
            localStorage.removeItem(`dirty-${selectedId}`);
        },
        [selectedId, mutateAsync, navigate, refetch, editorValue],
    );

    const handleOverwrite = React.useCallback(
        async (_ev: unknown) => {
            await handleSave(_ev, true);
        },
        [handleSave],
    );

    // save any changes to local storage before navigating away
    const saveDirty = React.useCallback(() => {
        if (!dirty) {
            return;
        }
        localStorage.setItem(`dirty-${entityId}`, editorValue);
    }, [editorValue, entityId, dirty]);

    const location = useLocation();
    useEffect(() => {
        saveDirty();
    }, [saveDirty, location]);

    useBeforeUnload(saveDirty);

    // load any unsaved changes from local storage (except for the new entities)
    const [unsavedChanges, setUnsavedChanges] = React.useState<string | null>(null);
    useEffect(() => {
        if (entityId === '_new') {
            return;
        }
        const unsaved = localStorage.getItem(`dirty-${entityId}`);
        if (unsaved) {
            setUnsavedChanges(unsaved);
        }
    }, [entityId]);
    const loadUnsavedChanges = React.useCallback(() => {
        if (unsavedChanges == null) {
            return;
        }
        setEditorValue(unsavedChanges);
        setDirty(true);
        localStorage.removeItem(`dirty-${entityId}`);
        setUnsavedChanges(null);
    }, [entityId, unsavedChanges]);

    const handleReset = React.useCallback(() => {
        localStorage.removeItem(`dirty-${entityId}`);
        setEditorValue(serverValue ?? '');
        setDirty(false);
        setUnsavedChanges(null);
    }, [entityId, serverValue]);

    const lastNamePart = useMemo(() => selectedName?.split('/').pop(), [selectedName]);

    return (
        <>
            <Snackbar
                open={showSuccess}
                autoHideDuration={5000}
                onClose={handleSuccessClose}
                message="Data saved successfully"
            />
            <Dialog open={unsavedChanges !== null}>
                <DialogTitle>Unsaved Changes</DialogTitle>
                <DialogContent>
                    You have unsaved changes from a previous session. Would you like to load them?
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleReset} color="warning">
                        Discard
                    </Button>
                    <Button onClick={loadUnsavedChanges} variant="contained">
                        Load
                    </Button>
                </DialogActions>
            </Dialog>
            <Box display="flex" flexDirection="column" height="100%">
                <Box margin={2}>
                    <ActionBar>
                        <PageTitle help={HELP}>
                            {selectedName && (
                                <>
                                    <PathBreadcrumbs path={selectedName}>
                                        {selectedId && (
                                            <>
                                                &nbsp;
                                                <Link
                                                    component={RouterLink}
                                                    to={`/entity/${selectedId}/details`}>
                                                    {'/'}
                                                    {lastNamePart ? lastNamePart : '?'}
                                                </Link>
                                            </>
                                        )}
                                    </PathBreadcrumbs>
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
                                            onChange={handlePreviewSwitch}
                                        />
                                    }
                                    label="Preview"
                                />
                            </FormControl>
                        )}
                        {selectedId !== undefined &&
                            selectedId !== '_new' &&
                            selectedKind === MICA_DASHBOARD_KIND && (
                                <FormControl>
                                    <Button
                                        startIcon={<PreviewIcon />}
                                        variant="outlined"
                                        disabled={isFetching || isSaving || dirty}
                                        onClick={() => navigate(`/dashboard/${selectedId}`)}>
                                        View
                                    </Button>
                                </FormControl>
                            )}
                        {selectedId !== undefined && selectedId !== '_new' && (
                            <ResetButton
                                disabled={isFetching || isSaving || !dirty}
                                onConfirm={handleReset}
                            />
                        )}
                        <Button
                            disabled={isFetching || isSaving || !dirty}
                            startIcon={<SaveIcon />}
                            variant="contained"
                            onClick={handleSave}>
                            Save
                        </Button>
                    </ActionBar>
                    {saveError && (
                        <Alert severity="error" sx={{ mt: 1 }}>
                            <ReadableApiError error={saveError} />
                            {saveError.status === 409 && (
                                <Tooltip title="Overwrite existing entity, ignoring current updatedAt value">
                                    <Button
                                        size="small"
                                        variant="outlined"
                                        sx={{ ml: 2 }}
                                        onClick={handleOverwrite}>
                                        Overwrite
                                    </Button>
                                </Tooltip>
                            )}
                        </Alert>
                    )}
                </Box>
                <Box flex="1">
                    <YamlEditor
                        entityKind={selectedKind}
                        value={editorValue}
                        onChange={handleEditorOnChange}
                        isLoading={isLoading}
                        isFetching={isFetching}
                        isSaving={isSaving}
                    />
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
