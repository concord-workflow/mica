import { EntityWithData, STANDARD_ENTITY_PROPERTIES, getEntity } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import highlightSubstring from '../components/highlight.tsx';
import EditIcon from '@mui/icons-material/Edit';
import {
    Button,
    CircularProgress,
    Container,
    Link,
    Paper,
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

const renderPropertyValue = (o: object, key: string): string =>
    JSON.stringify((o as Record<string, object>)[key], null, 2);

const searchProperties = (entity: EntityWithData, search: string): Array<string> => {
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
            enabled: entityId !== undefined,
        },
    );

    const [search, setSearch] = React.useState<string>('');

    const navigate = useNavigate();

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            <Grid container>
                <Grid xs={10}>
                    <PageTitle help={HELP}>Entity Details</PageTitle>
                </Grid>
                <Grid xs={2} display="flex" justifyContent="flex-end">
                    <div>
                        <Button
                            startIcon={<EditIcon />}
                            variant="contained"
                            onClick={() => navigate(`/entity/${entityId}/edit`)}>
                            Edit
                        </Button>
                    </div>
                </Grid>
            </Grid>
            <MetadataGrid sx={{ marginBottom: 2 }}>
                <MetadataItem label="ID">
                    {entityId} {isFetching && <CircularProgress size={16} />}
                </MetadataItem>
                <MetadataItem label="Name">{entity ? entity.name : '?'}</MetadataItem>
                <MetadataItem label="Kind">
                    {entity ? (
                        <Link
                            component={RouterLink}
                            to={`/redirect?type=entityByName&entityName=${entity.kind}`}>
                            {highlightSubstring(entity.kind, search)}
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
            {entity && (
                <>
                    <Typography variant="h6">Data</Typography>
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
                                {searchProperties(entity, search)
                                    .sort()
                                    .map((key) => (
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
