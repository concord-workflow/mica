import { Entity, MICA_VIEW_KIND, STANDARD_ENTITY_PROPERTIES, getEntity } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import highlightSubstring from '../components/highlight.tsx';
import DeleteEntityConfirmation from '../features/DeleteEntityConfirmation.tsx';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import {
    Button,
    CircularProgress,
    Container,
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
    Typography,
} from '@mui/material';
import Grid from '@mui/material/Unstable_Grid2';
import { Theme } from '@mui/material/styles';
import { SxProps } from '@mui/system';

import React from 'react';
import { useQuery } from 'react-query';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Entity Details</b> page provides overview of an entity.
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

const renderPropertyValue = (o: object, key: string) => {
    let json = JSON.stringify((o as Record<string, object>)[key], null, 2);
    if (json.length > 1000) {
        json = json.substring(0, 1000) + '...[cut]';
    }
    return json;
};

const searchProperties = (entity: Entity, search: string): Array<string> => {
    const searchLower = search.toLowerCase();
    return Object.keys(entity)
        .filter((key) => !STANDARD_ENTITY_PROPERTIES.includes(key))
        .filter((key) => key.toLowerCase().includes(searchLower));
};

interface MetadataGridProps {
    sx?: SxProps<Theme>;
    children: React.ReactNode;
}

const MetadataGrid = ({ sx, children }: MetadataGridProps) => (
    <Grid container sx={{ fontFamily: 'Roboto Mono', ...sx }}>
        {children}
    </Grid>
);

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

const EntityDetailsPage = () => {
    const { entityId } = useParams<RouteParams>();

    const { data: entity, isFetching } = useQuery(
        ['entity', entityId],
        () => getEntity(entityId!),
        {
            keepPreviousData: false,
            enabled: entityId !== undefined,
        },
    );

    const [search, setSearch] = React.useState<string>('');

    const navigate = useNavigate();

    const [openDeleteConfirmation, setOpenDeleteConfirmation] = React.useState(false);
    const handleDelete = React.useCallback(() => {
        if (!entity) {
            return;
        }
        setOpenDeleteConfirmation(true);
    }, [entity]);

    const visibleProperties = React.useMemo(
        () => (entity ? searchProperties(entity, search).sort() : []),
        [entity, search],
    );

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            {entity && (
                <DeleteEntityConfirmation
                    entityId={entity.id}
                    entityName={entity.name}
                    open={openDeleteConfirmation}
                    onSuccess={() => navigate('/entity')}
                    onClose={() => setOpenDeleteConfirmation(false)}
                />
            )}
            <Grid container>
                <Grid xs={10}>
                    <PageTitle help={HELP}>Entity Details</PageTitle>
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
                    {entityId} {isFetching && <CircularProgress size={16} />}
                </MetadataItem>
                <MetadataItem label="Name">{entity ? entity.name : '?'}</MetadataItem>
                <MetadataItem label="Kind">
                    {entity ? (
                        <Link
                            component={RouterLink}
                            to={`/redirect?type=entityByName&entityName=${entity.kind}`}>
                            {entity.kind}
                        </Link>
                    ) : (
                        '?'
                    )}
                </MetadataItem>
                <MetadataItem label="Created">
                    {entity ? new Date(entity.createdAt).toLocaleString() : '?'}
                </MetadataItem>
                <MetadataItem label="Updated">
                    {entity ? new Date(entity.updatedAt).toLocaleString() : '?'}
                </MetadataItem>
            </MetadataGrid>
            {entity && entity.kind == MICA_VIEW_KIND && (
                <FormControl sx={{ mt: 2, mb: 2 }}>TODO</FormControl>
            )}
            {entity && (
                <>
                    <Typography variant="h6">{kindToPayloadTitle(entity.kind)}</Typography>
                    <ActionBar sx={{ mb: 2 }}>
                        <Spacer />
                        <SearchField onChange={(search) => setSearch(search)} />
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
                                            <pre>{renderPropertyValue(entity, key)}</pre>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </>
            )}
        </Container>
    );
};

export default EntityDetailsPage;
