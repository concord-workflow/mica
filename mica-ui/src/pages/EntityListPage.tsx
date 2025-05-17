import { Entry, list } from '../api/entityList.ts';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import DeleteEntityConfirmation from '../features/DeleteEntityConfirmation.tsx';
import UploadEntityDialog from '../features/UploadEntityDialog.tsx';
import EntityTable from '../features/entityTable/EntityTable.tsx';
import EntityTableActionBar from '../features/entityTable/EntityTableActionBar.tsx';
import DeleteIcon from '@mui/icons-material/Delete';
import {
    Box,
    Container,
    Link,
    Paper,
    Snackbar,
    TableContainer,
    Tooltip,
    styled,
} from '@mui/material';

import { useQuery } from '@tanstack/react-query';
import React from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

const InlineDeleteIcon = styled(DeleteIcon)(() => ({
    position: 'relative',
    top: '3px',
}));

const HELP: React.ReactNode = (
    <>
        <b>Entities</b> page provides overview of all entities registered in Mica. Use the{' '}
        <b>upload</b> feature to import entities from YAML files.
    </>
);

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
        <Container sx={{ mt: 2, mb: 2 }} maxWidth="xl">
            <PageTitle help={HELP}>Entities</PageTitle>
            {openUpload && (
                <UploadEntityDialog
                    open={true}
                    onSuccess={handleSuccessfulUpload}
                    onClose={() => setOpenUpload(false)}
                />
            )}
            {selectedEntry && (
                <DeleteEntityConfirmation
                    type={selectedEntry.type}
                    entityId={selectedEntry.entityId}
                    entityName={selectedEntry.name}
                    entityPath={selectedPath === '/' ? '' : selectedPath}
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
            <EntityTableActionBar
                canCreate={true}
                selectedPath={selectedPath}
                selectedKind={selectedKind}
                handleKindSelect={setSelectedKind}
                search={search}
                handleSearch={setSearch}
            />
            <Box sx={{ mb: 2 }}>
                <PathBreadcrumbs prefix="/entity" path={selectedPath} />
            </Box>
            <TableContainer component={Paper}>
                <EntityTable
                    pathPrefix="/entity"
                    isFetching={isFetching}
                    data={data}
                    search={search}
                    selectedPath={selectedPath}
                    handleDelete={handleDelete}
                />
            </TableContainer>

            <Box mt={2} display="flex" justifyContent="end">
                <Tooltip title="Show deleted entities">
                    <Link component={RouterLink} to="/trash">
                        <InlineDeleteIcon />
                        Trash
                    </Link>
                </Tooltip>
            </Box>
        </Container>
    );
};

export default EntityListPage;
