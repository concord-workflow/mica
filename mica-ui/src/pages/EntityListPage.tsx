import { EntityEntry, OrderBy, listEntities } from '../api/entity.ts';
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

import React from 'react';
import { useQuery } from 'react-query';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Entities</b> page provides overview of all entities registered in Mica. Use the{' '}
        <b>upload</b> feature to import entities from YAML files.
    </>
);

const DEFAULT_ROW_LIMIT = 1000;

interface FileRow {
    key: string;
    label: string;
    type: 'entity';
    entity: EntityEntry;
}

interface FolderRow {
    key: string;
    label: string;
    type: 'folder';
    path: string;
}

type DataRow = FileRow | FolderRow;

/**
 * Takes a list of entities and returns a list of rows to be displayed in the table.
 * Each row can be either a "file" (link to an entity) or a "folder" (link to
 * the entity list filtered by the folder's path).
 */
const filterAndSortData = (data: EntityEntry[], selectedPath: string): DataRow[] => {
    const files = new Array<FileRow>();
    const folders = new Array<FolderRow>();

    data.filter((entity) => entity.name.startsWith(selectedPath)).forEach((entity) => {
        let path = entity.name.substring(0, entity.name.lastIndexOf('/'));
        if (path.length === 0) {
            path = '/';
        }
        const fileName = entity.name.substring(entity.name.lastIndexOf('/') + 1);
        if (path === selectedPath) {
            files.push({
                key: entity.id,
                label: fileName,
                type: 'entity',
                entity: entity,
            });
        } else {
            const relativePath = entity.name.substring(selectedPath.length);
            let nextFolder = relativePath.substring(0, relativePath.indexOf('/', 1));
            if (nextFolder[0] !== '/') {
                nextFolder = '/' + nextFolder;
            }
            if (!folders.find((folder) => folder.label === nextFolder)) {
                folders.push({
                    key: entity.id,
                    label: nextFolder,
                    type: 'folder',
                    path: (selectedPath + nextFolder).replace('//', '/'),
                });
            }
        }
    });

    return [
        ...folders.sort((a, b) => a.label.localeCompare(b.label)),
        ...files.sort((a, b) => a.label.localeCompare(b.label)),
    ];
};

const EntityTableRow = ({
    row,
    search,
    handleDelete,
}: {
    row: FileRow;
    search: string;
    handleDelete: (row: EntityEntry) => void;
}) => {
    return (
        <TableRow>
            <TableCell>
                <Tooltip title={row.entity.kind}>{entityKindToIcon(row.entity.kind)}</Tooltip>
            </TableCell>
            <TableCell>
                <Link component={RouterLink} to={`/entity/${row.entity.id}/details`}>
                    {highlightSubstring(row.label, search)}
                </Link>
            </TableCell>
            <TableCell align="right">
                <RowMenu>
                    <MenuItem onClick={() => handleDelete(row.entity)}>
                        <ListItemIcon>
                            <DeleteIcon fontSize="small" />
                        </ListItemIcon>
                        <ListItemText>Delete</ListItemText>
                    </MenuItem>
                </RowMenu>
            </TableCell>
        </TableRow>
    );
};

const FolderTableRow = ({ row, search }: { row: FolderRow; search: string }) => {
    return (
        <TableRow>
            <TableCell>
                <Tooltip title="Folder">
                    <FolderIcon />
                </Tooltip>
            </TableCell>
            <TableCell>
                <Link component={RouterLink} to={`/entity?path=${row.path}`}>
                    {highlightSubstring(row.label, search)}
                </Link>
            </TableCell>
            <TableCell align="right"></TableCell>
        </TableRow>
    );
};

const EntityListPage = () => {
    const [searchParams] = useSearchParams();

    const [openUpload, setOpenUpload] = React.useState(false);

    const [search, setSearch] = React.useState<string>('');
    const selectedPath = searchParams.get('path') ?? '/';
    const [selectedKind, setSelectedKind] = React.useState<string | undefined>(
        searchParams.get('kind') ?? undefined,
    );
    const { data, isFetching } = useQuery(
        ['entity', 'list', selectedPath, selectedKind, search],
        () =>
            listEntities({
                search,
                entityNameStartsWith: selectedPath,
                entityKind: selectedKind,
                orderBy: OrderBy.NAME,
                limit: DEFAULT_ROW_LIMIT,
            }),
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

    const effectiveData = React.useMemo(
        () => filterAndSortData(data ?? [], selectedPath),
        [data, selectedPath],
    );

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
                <SearchField onChange={(value) => setSearch(value)} />
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
                        {effectiveData.length > 0 &&
                            effectiveData.map((row) => {
                                if (row.type === 'folder') {
                                    return (
                                        <FolderTableRow key={row.key} row={row} search={search} />
                                    );
                                } else {
                                    return (
                                        <EntityTableRow
                                            key={row.key}
                                            row={row}
                                            search={search}
                                            handleDelete={handleDelete}
                                        />
                                    );
                                }
                            })}
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
