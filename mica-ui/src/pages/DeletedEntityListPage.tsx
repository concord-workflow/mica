import { list } from '../api/entityList.ts';
import PageTitle from '../components/PageTitle.tsx';
import PathBreadcrumbs from '../components/PathBreadcrumbs.tsx';
import EntityTable from '../features/entityTable/EntityTable.tsx';
import EntityTableActionBar from '../features/entityTable/EntityTableActionBar.tsx';
import { Box, Container, Paper, TableContainer } from '@mui/material';

import { useQuery } from '@tanstack/react-query';
import React from 'react';
import { useSearchParams } from 'react-router-dom';

const HELP: React.ReactNode = (
    <>
        <b>Trash</b> page shows previously deleted entities.
    </>
);

const DeletedEntityListPage = () => {
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
        queryKey: ['entity', 'listDeleted', selectedPath, selectedKind, search],
        queryFn: () => list(selectedPath, selectedKind, search, true),
        placeholderData: (prev) => prev,
        select: ({ data }) => data,
    });

    return (
        <Container sx={{ mt: 2, mb: 2 }} maxWidth="xl">
            <PageTitle help={HELP}>Deleted Entities</PageTitle>
            <EntityTableActionBar
                canCreate={false}
                canDownload={false}
                selectedPath={selectedPath}
                selectedKind={selectedKind}
                handleKindSelect={setSelectedKind}
                search={search}
                handleSearch={setSearch}
            />
            <Box sx={{ mb: 2 }}>
                <PathBreadcrumbs prefix="/trash" path={selectedPath} />
            </Box>
            <TableContainer component={Paper}>
                <EntityTable
                    pathPrefix="/trash"
                    isFetching={isFetching}
                    data={data}
                    search={search}
                    selectedPath={selectedPath}
                />
            </TableContainer>
        </Container>
    );
};

export default DeletedEntityListPage;
