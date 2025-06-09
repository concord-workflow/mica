import {
    Entity,
    MICA_DASHBOARD_KIND,
    MICA_VIEW_KIND,
    STANDARD_ENTITY_PROPERTIES,
    getEntity,
} from '../api/entity.ts';
import { EntryType } from '../api/entityList.ts';
import { ApiError } from '../api/error.ts';
import { ObjectSchemaNode } from '../api/schema.ts';
import ActionBar from '../components/ActionBar.tsx';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import ReadableApiError from '../components/ReadableApiError.tsx';
import SearchField from '../components/SearchField.tsx';
import SectionTitle from '../components/SectionTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import highlightSubstring from '../components/highlight.tsx';
import DeleteEntityConfirmation from '../features/DeleteEntityConfirmation.tsx';
import EntityChangesTable from '../features/history/EntityChangesTable.tsx';
import RenderView from '../features/views/RenderView.tsx';
import ViewParameters from '../features/views/ViewParameters.tsx';
import DeleteIcon from '@mui/icons-material/Delete';
import DownloadIcon from '@mui/icons-material/Download';
import EditIcon from '@mui/icons-material/Edit';
import PreviewIcon from '@mui/icons-material/Preview';
import ShareIcon from '@mui/icons-material/Share';
import {
    Alert,
    Box,
    Button,
    CircularProgress,
    Container,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    Grid,
    Link,
    List,
    ListItem,
    ListSubheader,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from '@mui/material';
import { Theme } from '@mui/material/styles';
import { SxProps } from '@mui/system';

import { useQuery } from '@tanstack/react-query';
import React, { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <p>
            <b>Entity Details</b> page shows the properties of a single entity. Only top-level
            properties are shown. The values are rendered as JSON.
        </p>
        <p>
            Use <b>Search</b> to search by property name.
        </p>
        <p>
            Depending on the entity's <b>kind</b>, additional actions are available. For example,
            views (<i>/mica/view/v1</i> entities) can be previewed by clicking on the <b>Preview</b>{' '}
            button. The view parameters can be set in the dialog.
        </p>
    </>
);

type RouteParams = {
    entityId: string;
};

const kindToPayloadTitle = (kind: string): string => {
    switch (kind) {
        case MICA_VIEW_KIND:
            return 'View Definition';
        default:
            return 'Data';
    }
};

const MAX_PROPERTY_TEXT_LENGTH = 1000;

const Property = ({ name, value, search }: { name: string; value: object; search: string }) => {
    const [expand, setExpand] = useState<boolean>(false);
    let text = JSON.stringify((value as Record<string, object>)[name], null, 2);
    if (search === '' && !expand && text.length > MAX_PROPERTY_TEXT_LENGTH) {
        text =
            text.substring(0, MAX_PROPERTY_TEXT_LENGTH) +
            `...[${text.length - MAX_PROPERTY_TEXT_LENGTH} more character(s)]`;
        return (
            <>
                <pre style={{ fontFamily: 'Fira Mono' }}>{text}</pre>
                <Button size="small" onClick={() => setExpand(true)}>
                    Expand
                </Button>
            </>
        );
    }
    return <pre style={{ fontFamily: 'Fira Mono' }}>{highlightSubstring(text, search)}</pre>;
};

const searchProperties = (entity: Entity, search: string): Array<string> => {
    const searchLower = search.toLowerCase();
    return Object.entries(entity)
        .filter(([key]) => !STANDARD_ENTITY_PROPERTIES.includes(key))
        .filter(([key, value]) => {
            const keyText = key.toLowerCase();
            let valueText;
            if (typeof value === 'string') {
                valueText = value.toLowerCase();
            } else {
                valueText = JSON.stringify(value).toLowerCase();
            }
            return keyText.includes(searchLower) || valueText.includes(searchLower);
        })
        .map(([key]) => key);
};

interface MetadataGridProps {
    sx?: SxProps<Theme>;
    children: React.ReactNode;
}

const MetadataGrid = ({ sx, children }: MetadataGridProps) => {
    return (
        <>
            <Grid
                container={true}
                spacing={1}
                sx={{ fontFamily: 'Fira Mono', fontSize: 12, ...sx }}>
                {children}
            </Grid>
        </>
    );
};

interface MetadataItemProps {
    label: string;
    children: React.ReactNode;
}

const MetadataItem = ({ label, children }: MetadataItemProps) => (
    <>
        <Grid size={2} sx={{ minHeight: '28px' }}>
            {label}
        </Grid>
        <Grid size={10}>{children}</Grid>
    </>
);

interface ShareButtonProps {
    entityId: string | undefined;
    entityName: string | undefined;
    entityKind: string | undefined;
}

const ShareButton = ({ entityId, entityName, entityKind }: ShareButtonProps) => {
    const [open, setOpen] = React.useState<boolean>(false);

    if (!entityName) {
        return <></>;
    }

    const permaLink = `${window.location.protocol}//${window.location.host}/mica/entity/${entityId}/details`;
    const humanLink = `${window.location.protocol}//${window.location.host}/mica/redirect?type=entityByName&entityName=${entityName}`;
    const curl = `curl -H 'Authorization: Bearer your_token' ${window.location.protocol}//${window.location.host}/api/mica/v1/view/render/${entityId}`;

    return (
        <>
            <Dialog open={open} onClose={() => setOpen(false)} maxWidth="lg">
                <DialogTitle>Share</DialogTitle>
                <DialogContent>
                    <List>
                        <ListSubheader>Permalink</ListSubheader>
                        <ListItem
                            sx={{ fontFamily: 'Fira Mono', fontSize: '14px' }}
                            secondaryAction={
                                <CopyToClipboardButton
                                    text={permaLink}
                                    tooltipText="Copy permalink"
                                />
                            }>
                            {permaLink}
                        </ListItem>
                        <ListSubheader sx={{ mt: 4 }}>Human-readable link</ListSubheader>
                        <ListItem
                            sx={{ fontFamily: 'Fira Mono', fontSize: '14px' }}
                            secondaryAction={
                                <CopyToClipboardButton
                                    text={humanLink}
                                    tooltipText="Copy human-readable link"
                                />
                            }>
                            {humanLink}
                        </ListItem>
                        {entityKind == MICA_VIEW_KIND && (
                            <>
                                <ListSubheader sx={{ mt: 4 }}>Render with cURL</ListSubheader>
                                <ListItem
                                    sx={{ fontFamily: 'Fira Mono', fontSize: '14px' }}
                                    secondaryAction={
                                        <CopyToClipboardButton
                                            text={curl}
                                            tooltipText="Copy curl command to render the view. Parametrized views might require a POST request."
                                        />
                                    }>
                                    {curl}
                                </ListItem>
                            </>
                        )}
                    </List>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpen(false)}>Close</Button>
                </DialogActions>
            </Dialog>
            <Button variant="outlined" startIcon={<ShareIcon />} onClick={() => setOpen(true)}>
                Share
            </Button>
        </>
    );
};

const PropertiesFound = ({ count }: { count: number }) => {
    return (
        <Typography variant="body2">
            {count} propert{count === 1 ? 'y' : 'ies'} found
        </Typography>
    );
};

const PreviewDialog = ({ data, onClose }: { data: Entity; onClose: () => void }) => {
    const [requestParameters, setRequestParameters] = React.useState<Record<string, string | null>>(
        {},
    );

    const handleParameterChange = React.useCallback((key: string, value: string) => {
        setRequestParameters((prev) => ({ ...prev, [key]: value === '' ? null : value }));
    }, []);

    return (
        <Dialog open={true} fullScreen={true}>
            <DialogTitle>
                <Grid container spacing={2}>
                    <Grid flex={1}>
                        Preview of <code>{data.name}</code>
                    </Grid>
                    <Grid>
                        <Button variant="contained" onClick={onClose}>
                            Close
                        </Button>
                    </Grid>
                </Grid>
            </DialogTitle>
            <DialogContent>
                <Grid container spacing={1} height="100%">
                    <Grid size={3}>
                        <ViewParameters
                            parameters={data.parameters as ObjectSchemaNode}
                            values={requestParameters}
                            onChange={handleParameterChange}
                        />
                    </Grid>
                    <Grid size={9}>
                        <RenderView request={{ viewId: data.id, parameters: requestParameters }} />
                    </Grid>
                </Grid>
            </DialogContent>
        </Dialog>
    );
};

const PageContainer = ({ children }: { children: React.ReactNode }) => {
    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            {children}
        </Container>
    );
};

const EntityDetailsPage = () => {
    const { entityId } = useParams<RouteParams>();

    const { data, isFetching, error } = useQuery<Entity, ApiError>({
        queryKey: ['entity', entityId],
        queryFn: () => getEntity(entityId!),
        enabled: entityId !== undefined,
        retry: (failureCount, error) => error.status !== 404 && failureCount < 3,
    });

    const [search, setSearch] = React.useState<string>('');

    const navigate = useNavigate();

    const [openDeleteConfirmation, setOpenDeleteConfirmation] = React.useState(false);
    const handleDelete = React.useCallback(() => {
        if (!data) {
            return;
        }
        setOpenDeleteConfirmation(true);
    }, [data]);

    const visibleProperties = React.useMemo(
        () => (data ? searchProperties(data, search).sort() : []),
        [data, search],
    );

    const [showPreview, setShowPreview] = React.useState(false);

    if (error) {
        return (
            <PageContainer>
                <Alert color="error">
                    <ReadableApiError error={error} />
                </Alert>
            </PageContainer>
        );
    }

    if (!data) {
        return (
            <PageContainer>
                <CircularProgress />
            </PageContainer>
        );
    }

    const deleted = data['deletedAt'] !== undefined;

    return (
        <PageContainer>
            {!deleted && (
                <DeleteEntityConfirmation
                    type={EntryType.FILE}
                    entityId={data.id}
                    entityName={data.name}
                    open={openDeleteConfirmation}
                    onSuccess={() => navigate('/entity')}
                    onClose={() => setOpenDeleteConfirmation(false)}
                />
            )}
            <Grid container spacing={1}>
                <Grid flex={1}>
                    <PageTitle help={HELP}>
                        <>
                            <PathBreadcrumbs prefix="/entity" path={data.name} />
                            {deleted && (
                                <Box
                                    component={'span'}
                                    sx={(theme) => ({ ml: 1, color: theme.palette.error.main })}>
                                    (deleted)
                                </Box>
                            )}
                            <CopyToClipboardButton text={data.name} />
                        </>
                    </PageTitle>
                </Grid>
                <Grid>
                    <FormControl>
                        <ShareButton
                            entityId={data?.id}
                            entityName={data?.name}
                            entityKind={data?.kind}
                        />
                    </FormControl>
                </Grid>
                <Grid>
                    <FormControl>
                        <Button
                            variant="outlined"
                            startIcon={<DownloadIcon />}
                            target="_blank"
                            href={`/api/mica/v1/entity/${entityId}/download`}>
                            Download
                        </Button>
                    </FormControl>
                </Grid>
                {data.kind === MICA_DASHBOARD_KIND && (
                    <Grid>
                        <FormControl>
                            <Button
                                startIcon={<PreviewIcon />}
                                variant="outlined"
                                onClick={() => navigate(`/dashboard/${entityId}`)}>
                                View
                            </Button>
                        </FormControl>
                    </Grid>
                )}
                {data.kind == MICA_VIEW_KIND && (
                    <Grid>
                        <FormControl>
                            <Button
                                startIcon={<PreviewIcon />}
                                variant="outlined"
                                onClick={() => setShowPreview(true)}>
                                Preview
                            </Button>
                        </FormControl>
                    </Grid>
                )}
                {showPreview && <PreviewDialog data={data} onClose={() => setShowPreview(false)} />}
                {!deleted && (
                    <Grid>
                        <FormControl>
                            <Tooltip title="Permanently delete this entity. This action cannot be undone.">
                                <span>
                                    <Button
                                        startIcon={<DeleteIcon />}
                                        variant="outlined"
                                        color="error"
                                        onClick={handleDelete}
                                        disabled={isFetching}>
                                        Delete
                                    </Button>
                                </span>
                            </Tooltip>
                        </FormControl>
                    </Grid>
                )}
                <Grid>
                    <FormControl>
                        <Button
                            startIcon={<EditIcon />}
                            variant="contained"
                            onClick={() => navigate(`/entity/${entityId}/edit`)}>
                            Edit
                        </Button>
                    </FormControl>
                </Grid>
            </Grid>
            <MetadataGrid sx={{ mb: 2 }}>
                <MetadataItem label="ID">
                    {entityId} {entityId && <CopyToClipboardButton text={entityId} />}{' '}
                    {isFetching && <CircularProgress size={16} />}
                </MetadataItem>
                <MetadataItem label="Kind">
                    <Link
                        component={RouterLink}
                        to={`/redirect?type=entityByName&entityName=${data.kind}`}>
                        {data.kind}
                    </Link>
                    <CopyToClipboardButton text={data.kind} />
                </MetadataItem>
                <MetadataItem label="Created">
                    {new Date(data.createdAt).toLocaleString()}
                </MetadataItem>
                <MetadataItem label="Updated">
                    {new Date(data.updatedAt).toLocaleString()}
                </MetadataItem>
                {deleted && (
                    <MetadataItem label="Deleted">
                        {new Date(data['deletedAt'] as string).toLocaleString()}
                    </MetadataItem>
                )}
            </MetadataGrid>

            <Divider />
            <SectionTitle>{kindToPayloadTitle(data.kind)}</SectionTitle>
            <ActionBar sx={{ mb: 2 }}>
                <Spacer />
                {search !== '' && <PropertiesFound count={visibleProperties.length} />}
                <SearchField value={search} onChange={(search) => setSearch(search)} />
            </ActionBar>
            <TableContainer component={Paper}>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell width="20%">Property</TableCell>
                            <TableCell>Value</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {visibleProperties.map((key) => (
                            <TableRow key={key}>
                                <TableCell
                                    sx={{
                                        verticalAlign: 'top',
                                        fontFamily: 'Fira Mono',
                                    }}>
                                    {highlightSubstring(key, search)}
                                </TableCell>
                                <TableCell
                                    sx={{
                                        verticalAlign: 'top',
                                        fontFamily: 'Fira Mono',
                                    }}>
                                    <Property name={key} value={data} search={search} />
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
            {entityId && (
                <>
                    <Divider sx={{ mt: 10, mb: 2 }} />
                    <EntityChangesTable entityId={entityId} />
                </>
            )}
        </PageContainer>
    );
};

export default EntityDetailsPage;
