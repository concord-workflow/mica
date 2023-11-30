import { EntityEntry, OrderBy, listEntities } from '../api/entity.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import RowMenu from '../components/RowMenu.tsx';
import SearchField from '../components/SearchField.tsx';
import Spacer from '../components/Spacer.tsx';
import entityKindToIcon from '../components/entityKindToIcon.tsx';
import highlightSubstring from '../components/highlight.tsx';
import CreateEntityButton from '../features/CreateEntityButton.tsx';
import DeleteEntityConfirmation from '../features/DeleteEntityConfirmation.tsx';
import EntityKindSelect from '../features/EntityKindSelect.tsx';
import UploadEntityDialog from '../features/UploadEntityDialog.tsx';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteIcon from '@mui/icons-material/Delete';
import {
    Button,
    CircularProgress,
    Container,
    FormControl,
    InputLabel,
    Link,
    MenuItem,
    Paper,
    Snackbar,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
} from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import React from 'react';
import { useQuery } from 'react-query';
import { Link as RouterLink } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Entities</b> page provides overview of all entities registered in Mica. Use the{' '}
        <b>upload</b> feature to import entities from YAML files.
    </>
);

const DEFAULT_ROW_LIMIT = 100;

const EntityListPage = () => {
    const [openUpload, setOpenUpload] = React.useState(false);

    const [search, setSearch] = React.useState<string>('');
    const [entityKindFilter, setEntityKindFilter] = React.useState<string | undefined>();
    const { data, isFetching } = useQuery(
        ['entity', 'list', entityKindFilter, search],
        () => listEntities(search, undefined, entityKindFilter, OrderBy.NAME, DEFAULT_ROW_LIMIT),
        {
            keepPreviousData: true,
            select: ({ data }) => data,
        },
    );

    const [successNotification, setSuccessNotification] = React.useState<string | undefined>();
    const handleSuccessfulUpload = React.useCallback(() => {
        setSuccessNotification('Data uploaded successfully');
        setOpenUpload(false);
    }, []);

    const [selectedEntity, setSelectedEntity] = React.useState<EntityEntry | undefined>();
    const [openDeleteConfirmation, setOpenDeleteConfirmation] = React.useState(false);
    const handleDelete = React.useCallback((entry: EntityEntry) => {
        setSelectedEntity(entry);
        setOpenDeleteConfirmation(true);
    }, []);
    const handleCancelDelete = React.useCallback(() => {
        setSelectedEntity(undefined);
        setOpenDeleteConfirmation(false);
    }, []);
    const handleSuccessfulDelete = React.useCallback(() => {
        setSuccessNotification('Entity deleted successfully');
        setOpenDeleteConfirmation(false);
    }, []);

    return (
        <Container sx={{ mt: 2 }} maxWidth="xl">
            <PageTitle help={HELP}>Entities</PageTitle>
            {openUpload && (
                <UploadEntityDialog
                    open={true}
                    onSuccess={handleSuccessfulUpload}
                    onClose={() => setOpenUpload(false)}
                />
            )}
            {selectedEntity && (
                <DeleteEntityConfirmation
                    entityId={selectedEntity.id}
                    entityName={selectedEntity.name}
                    open={openDeleteConfirmation}
                    onSuccess={handleSuccessfulDelete}
                    onClose={handleCancelDelete}
                />
            )}
            <Snackbar
                open={successNotification != undefined}
                autoHideDuration={5000}
                onClose={() => setSuccessNotification(undefined)}
                message={successNotification}
            />
            <ActionBar sx={{ mb: 2 }}>
                <FormControl>
                    <CreateEntityButton />
                </FormControl>
                <FormControl>
                    <Button
                        startIcon={<CloudUploadIcon />}
                        variant="outlined"
                        onClick={() => setOpenUpload(true)}>
                        Upload
                    </Button>
                </FormControl>
                <Spacer />
                <FormControl size="small" sx={{ minWidth: 200 }}>
                    <InputLabel>Kind</InputLabel>
                    <EntityKindSelect value={entityKindFilter} onChange={setEntityKindFilter} />
                </FormControl>
                <SearchField onChange={(value) => setSearch(value)} />
            </ActionBar>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell width="5%">Kind</TableCell>
                            <TableCell>Name</TableCell>
                            <TableCell align="right">
                                {isFetching && (
                                    <CircularProgress size={12} sx={{ marginRight: 1 }} />
                                )}
                            </TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data &&
                            data.length > 0 &&
                            data.map((row) => (
                                <TableRow key={row.id}>
                                    <TableCell>
                                        <Tooltip title={row.kind}>
                                            {entityKindToIcon(row.kind)}
                                        </Tooltip>
                                    </TableCell>
                                    <TableCell>
                                        <Link
                                            component={RouterLink}
                                            to={`/entity/${row.id}/details`}>
                                            {highlightSubstring(row.name, search)}
                                        </Link>
                                    </TableCell>
                                    <TableCell align="right">
                                        <RowMenu>
                                            <MenuItem onClick={() => handleDelete(row)}>
                                                <ListItemIcon>
                                                    <DeleteIcon fontSize="small" />
                                                </ListItemIcon>
                                                <ListItemText>Delete</ListItemText>
                                            </MenuItem>
                                        </RowMenu>
                                    </TableCell>
                                </TableRow>
                            ))}
                        {data && data.length >= DEFAULT_ROW_LIMIT && (
                            <TableRow>
                                <TableCell colSpan={3} align="center">
                                    More than {DEFAULT_ROW_LIMIT} results, please refine your
                                    search.
                                </TableCell>
                            </TableRow>
                        )}
                        {data && data.length < 1 && (
                            <TableRow>
                                <TableCell colSpan={3} align="center">
                                    No data
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Container>
    );
};

export default EntityListPage;
