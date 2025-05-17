import { ENTITY_SEARCH_LIMIT, Entry } from '../../api/entityList.ts';
import EntityTableRow from './EntityTableRow.tsx';
import {
    CircularProgress,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';

interface Props {
    pathPrefix: string;
    isFetching: boolean;
    data: Array<Entry> | undefined;
    search: string;
    handleDelete?: (row: Entry) => void;
    selectedPath: string;
}

const EntityTable = ({
    pathPrefix,
    isFetching,
    data,
    search,
    handleDelete,
    selectedPath,
}: Props) => {
    return (
        <Table>
            <TableHead>
                <TableRow>
                    <TableCell width="5%">Kind</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell align="right">
                        {isFetching && <CircularProgress size={12} sx={{ marginRight: 1 }} />}
                    </TableCell>
                </TableRow>
            </TableHead>
            <TableBody>
                {data &&
                    data.length > 0 &&
                    data.map((row) => (
                        <EntityTableRow
                            key={`${row.type}-${row.name}-${row.entityId}`}
                            pathPrefix={pathPrefix}
                            row={row}
                            search={search}
                            handleDelete={handleDelete}
                            selectedPath={selectedPath}
                        />
                    ))}
                {data && search !== '' && data.length >= ENTITY_SEARCH_LIMIT && (
                    <TableRow>
                        <TableCell colSpan={3} align="center">
                            More than {ENTITY_SEARCH_LIMIT} results, please refine your search.
                        </TableCell>
                    </TableRow>
                )}
                {data && search !== '' && data.length < ENTITY_SEARCH_LIMIT && (
                    <TableRow>
                        <TableCell colSpan={3} align="center">
                            No more search results.
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
    );
};

export default EntityTable;
