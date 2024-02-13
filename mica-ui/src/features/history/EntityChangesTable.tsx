import { listHistory } from '../../api/history.ts';
import SectionTitle from '../../components/SectionTitle.tsx';
import ViewHistoryEntryPopup from './ViewHistoryEntryPopup.tsx';
import {
    Button,
    CircularProgress,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';

import React from 'react';
import { useQuery } from 'react-query';

interface Props {
    entityId: string;
}

const EntityChangesTable = ({ entityId }: Props) => {
    const { data, isFetching } = useQuery(['history', entityId], () => listHistory(entityId!, 10), {
        keepPreviousData: false,
        enabled: entityId !== undefined,
        select: (data) => data.data,
    });

    const [selectedVersion, setSelectedVersion] = React.useState<string>();

    return (
        <>
            {data && data.length > 0 && (
                <>
                    <SectionTitle>
                        Changes {isFetching && <CircularProgress size={16} />}
                    </SectionTitle>
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
