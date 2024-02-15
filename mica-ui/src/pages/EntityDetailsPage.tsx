import { Entity, MICA_VIEW_KIND, STANDARD_ENTITY_PROPERTIES, getEntity } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import CopyToClipboardButton from '../components/CopyToClipboardButton.tsx';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import SearchField from '../components/SearchField.tsx';
import SectionTitle from '../components/SectionTitle.tsx';
import Spacer from '../components/Spacer.tsx';
import highlightSubstring from '../components/highlight.tsx';
import DeleteEntityConfirmation from '../features/DeleteEntityConfirmation.tsx';
import EntityChangesTable from '../features/history/EntityChangesTable.tsx';
import RenderView from '../features/views/RenderView.tsx';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import PreviewIcon from '@mui/icons-material/Preview';
import {
    Button,
    CircularProgress,
    Container,
    Divider,
    FormControl,
    Link,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from '@mui/material';
import Grid from '@mui/material/Unstable_Grid2';
import { Theme } from '@mui/material/styles';
import { SxProps } from '@mui/system';

import React, { useState } from 'react';
import { useQuery } from 'react-query';
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
            Views (<i>/mica/view/v1</i>) entities can be previewed by clicking on the{' '}
            <b>Preview data</b> button.
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
                <pre>{text}</pre>
                <Button size="small" onClick={() => setExpand(true)}>
                    Expand
                </Button>
            </>
        );
    }
    return <pre>{highlightSubstring(text, search)}</pre>;
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
            <Grid container sx={{ fontFamily: 'Roboto Mono', fontSize: 12, ...sx }}>
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
        <Grid xs={2}>{label}</Grid>
        <Grid xs={10}>{children}</Grid>
    </>
);

const PropertiesFound = ({ count }: { count: number }) => {
    return (
        <Typography variant="body2">
            {count} propert{count === 1 ? 'y' : 'ies'} found
        </Typography>
    );
};

const EntityDetailsPage = () => {
    const { entityId } = useParams<RouteParams>();

    // TODO handle errors

    const { data, isFetching } = useQuery(['entity', entityId], () => getEntity(entityId!), {
        keepPreviousData: false,
        enabled: entityId !== undefined,
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

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            {data && (
                <DeleteEntityConfirmation
                    entityId={data.id}
                    entityName={data.name}
                    open={openDeleteConfirmation}
                    onSuccess={() => navigate('/entity')}
                    onClose={() => setOpenDeleteConfirmation(false)}
                />
            )}
            <Grid container>
                <Grid xs={10}>
                    <PageTitle help={HELP}>
                        {data && (
                            <>
                                <PathBreadcrumbs path={data.name} />
                                <CopyToClipboardButton text={data.name} />
                            </>
                        )}
                    </PageTitle>
                </Grid>
                <Grid xs={2} display="flex" justifyContent="flex-end">
                    <Stack direction="row" spacing={2}>
                        <FormControl>
                            <Button
                                startIcon={<DeleteIcon />}
                                variant="outlined"
                                color="error"
                                onClick={handleDelete}
                                disabled={isFetching}>
                                Delete
                            </Button>
                        </FormControl>
                        <FormControl>
                            <Button
                                startIcon={<EditIcon />}
                                variant="contained"
                                onClick={() => navigate(`/entity/${entityId}/edit`)}>
                                Edit
                            </Button>
                        </FormControl>
                    </Stack>
                </Grid>
            </Grid>
            <MetadataGrid sx={{ mb: 2 }}>
                <MetadataItem label="ID">
                    {entityId} {entityId && <CopyToClipboardButton text={entityId} />}{' '}
                    {isFetching && <CircularProgress size={16} />}
                </MetadataItem>
                <MetadataItem label="Kind">
                    {data ? (
                        <>
                            <Link
                                component={RouterLink}
                                to={`/redirect?type=entityByName&entityName=${data.kind}`}>
                                {data.kind}
                            </Link>
                            <CopyToClipboardButton text={data.kind} />
                        </>
                    ) : (
                        '?'
                    )}
                </MetadataItem>
                <MetadataItem label="Created">
                    {data ? new Date(data.createdAt).toLocaleString() : '?'}
                </MetadataItem>
                <MetadataItem label="Updated">
                    {data ? new Date(data.updatedAt).toLocaleString() : '?'}
                </MetadataItem>
            </MetadataGrid>
            {data && data.kind == MICA_VIEW_KIND && (
                <FormControl sx={{ mt: 2, mb: 2 }}>
                    <Tooltip title="Render this view using a small subset of data">
                        <Button
                            startIcon={<PreviewIcon />}
                            onClick={() => setShowPreview((prev) => !prev)}>
                            {showPreview ? 'Hide preview' : 'Preview data'}
                        </Button>
                    </Tooltip>
                </FormControl>
            )}
            {entityId && showPreview && (
                <Paper sx={{ p: 2, mb: 2 }}>
                    <RenderView request={{ viewId: entityId, limit: 10 }} />
                </Paper>
            )}
            {data && (
                <>
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
                                                fontFamily: 'Roboto Mono',
                                            }}>
                                            {highlightSubstring(key, search)}
                                        </TableCell>
                                        <TableCell
                                            sx={{
                                                verticalAlign: 'top',
                                                fontFamily: 'Roboto Mono',
                                            }}>
                                            <Property name={key} value={data} search={search} />
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </>
            )}
            {entityId && (
                <>
                    <Divider sx={{ mt: 10 }} />
                    <EntityChangesTable entityId={entityId} />
                </>
            )}
        </Container>
    );
};

export default EntityDetailsPage;
