import { Entry, EntryType, list } from '../api/entityList.ts';
import ActionBar from '../components/ActionBar.tsx';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
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
import FolderIcon from '@mui/icons-material/Folder';
import {
    Box,
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
    Typography,
} from '@mui/material';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';

import { useQuery } from '@tanstack/react-query';
import React from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Entities</b> page provides overview of all entities registered in Mica. Use the{' '}
        <b>upload</b> feature to import entities from YAML files.
    </>
);

const SEARCH_LIMIT = 100;

const EntityTableRow = ({
    row,
    search,
    handleDelete,
    selectedPath,
}: {
    row: Entry;
    search: string;
    handleDelete: (row: Entry) => void;
    selectedPath: string;
}) => {
    return (
        <TableRow>
            <TableCell>
                {row.type === EntryType.FOLDER && (
                    <Tooltip title="Folder">
                        <FolderIcon />
                    </Tooltip>
                )}
                {row.entityKind && (
                    <Tooltip title={row.entityKind}>{entityKindToIcon(row.entityKind)}</Tooltip>
                )}
            </TableCell>
            <TableCell>
                {row.type === EntryType.FOLDER && (
                    <Link
                        component={RouterLink}
                        to={`/entity?path=${selectedPath}${
                            selectedPath === '/' ? '' : '/'
                        }${encodeURIComponent(row.name)}`}>
                        {highlightSubstring(row.name, search)}
                    </Link>
                )}
                {row.entityId && (
                    <Link component={RouterLink} to={`/entity/${row.entityId}/details`}>
                        {highlightSubstring(row.name, search)}
                    </Link>
                )}
            </TableCell>
            <TableCell align="right">
                {row.type === EntryType.FILE && (
                    <RowMenu>
                        <MenuItem onClick={() => handleDelete(row)}>
                            <ListItemIcon>
                                <DeleteIcon fontSize="small" />
                            </ListItemIcon>
                            <ListItemText>Delete</ListItemText>
                        </MenuItem>
                    </RowMenu>
                )}
            </TableCell>
        </TableRow>
    );
};

const EntityListPage = () => {
    const [searchParams] = useSearchParams();
    const selectedPath = searchParams.get('path') ?? '/';
    const [search, setSearch] = React.useState<string>(searchParams.get('search') ?? '');
    // reset search when path changes
    React.useEffect(() => {
        setSearch('');
    }, [selectedPath]);

    const [selectedKind, setSelectedKind] = React.useState<string | undefined>(
        searchParams.get('kind') ?? undefined,
    );
    const { data, isFetching } = useQuery({
        queryKey: ['entity', 'list', selectedPath, selectedKind, search],
        queryFn: () => list(selectedPath, selectedKind, search),

        placeholderData: (prev) => prev,
        select: ({ data }) => data,
    });

    const [openUpload, setOpenUpload] = React.useState(false);

    const [successNotification, setSuccessNotification] = React.useState<string | undefined>();
    const handleSuccessfulUpload = React.useCallback(() => {
        setSuccessNotification('Data uploaded successfully');
        setOpenUpload(false);
    }, []);

    const [selectedEntry, setSelectedEntry] = React.useState<Entry | undefined>();
    const [openDeleteConfirmation, setOpenDeleteConfirmation] = React.useState(false);
    const handleDelete = React.useCallback((entry: Entry) => {
        setSelectedEntry(entry);
        setOpenDeleteConfirmation(true);
    }, []);
    const handleCancelDelete = React.useCallback(() => {
        setSelectedEntry(undefined);
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
            {selectedEntry?.entityId && (
                <DeleteEntityConfirmation
                    entityId={selectedEntry.entityId}
                    entityName={selectedEntry.name}
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
                    <CreateEntityButton path={selectedPath} />
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
                    <EntityKindSelect value={selectedKind} onChange={setSelectedKind} />
                </FormControl>
                <SearchField value={search} onChange={(value) => setSearch(value)} />
            </ActionBar>
            <Box sx={{ mb: 2 }}>
                <PathBreadcrumbs path={selectedPath} />
            </Box>
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
                                <EntityTableRow
                                    key={row.name}
                                    row={row}
                                    search={search}
                                    handleDelete={handleDelete}
                                    selectedPath={selectedPath}
                                />
                            ))}
                        {data && search !== '' && data.length >= SEARCH_LIMIT && (
                            <TableRow>
                                <TableCell colSpan={3} align="center">
                                    More than {SEARCH_LIMIT} results, please refine your search.
                                </TableCell>
                            </TableRow>
                        )}
                        {data && data.length < 1 && (
                            <TableRow>
                                <TableCell colSpan={3} align="center">
                                    <Typography variant="caption">
                                        {isFetching ? 'Loading...' : 'No data'}
                                    </Typography>
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
