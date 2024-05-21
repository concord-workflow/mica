import { listHistory } from '../../api/history.ts';
import ViewHistoryEntryPopup from './ViewHistoryEntryPopup.tsx';
import {
    Button,
    CircularProgress,
    MenuItem,
    Select,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import Grid from '@mui/material/Unstable_Grid2';

import { useQuery } from '@tanstack/react-query';
import React from 'react';

interface Props {
    entityId: string;
}

const EntityChangesTable = ({ entityId }: Props) => {
    const [limit, setLimit] = React.useState(10);

    const { data, isFetching } = useQuery({
        queryKey: ['history', entityId, limit],
        queryFn: () => listHistory(entityId!, limit),
        enabled: entityId !== undefined,
        select: (data) => data.data,
    });

    const [selectedVersion, setSelectedVersion] = React.useState<string>();

    return (
        <>
            {data && data.length > 0 && (
                <>
                    <Grid spacing={2} sx={{ mb: 1 }} container={true}>
                        <Grid xs={6}>
                            <Typography variant="h6">
                                Changes {isFetching && <CircularProgress size={16} />}
                            </Typography>
                        </Grid>
                        <Grid xs={6} display="flex" justifyContent="end">
                            <Typography variant="caption">
                                Showing{' '}
                                <Select
                                    value={limit}
                                    onChange={(ev) => setLimit(ev.target.value as number)}
                                    sx={{ ml: 1, mr: 1, height: 32, fontSize: 'inherit' }}
                                    size="small">
                                    <MenuItem value={10}>10 recent</MenuItem>
                                    <MenuItem value={100}>100 recent</MenuItem>
                                    <MenuItem value={-1}>all</MenuItem>
                                </Select>{' '}
                                records.
                            </Typography>
                        </Grid>
                    </Grid>
                    {entityId && (
                        <ViewHistoryEntryPopup
                            open={selectedVersion !== undefined}
                            onClose={() => setSelectedVersion(undefined)}
                            entityId={entityId}
                            updatedAt={selectedVersion!}
                        />
                    )}
                    <TableContainer>
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Updated At</TableCell>
                                    <TableCell width="15%">Author</TableCell>
                                    <TableCell width="10%"></TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {data.map((entry) => (
                                    <TableRow key={entry.updatedAt}>
                                        <TableCell>
                                            {new Date(entry.updatedAt).toLocaleString()}
                                        </TableCell>
                                        <TableCell>{entry.author}</TableCell>
                                        <TableCell>
                                            <Button
                                                onClick={() => setSelectedVersion(entry.updatedAt)}>
                                                View
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </>
            )}
            {data && data.length === 0 && (
                <Typography variant="caption">No history data</Typography>
            )}
        </>
    );
};

export default EntityChangesTable;
